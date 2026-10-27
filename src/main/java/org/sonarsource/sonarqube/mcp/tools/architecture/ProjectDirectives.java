/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.architecture;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

final class ProjectDirectives {
  private static final int MAX_DIRECTIVES_PER_PROJECT = 1000;
  private static final String CREATE = "create";
  private static final String UPDATE = "update";
  private static final String DELETE = "delete";
  private static final Set<String> FIELDS = Set.of("id", "projectId", "type", "ecosystem", "graphType", "fromNodeKey", "toNodeKey",
    "graphPerspective", "description", "origin");

  private ProjectDirectives() {
  }

  static Changes parse(String json, String projectKey) {
    JsonElement value;
    try {
      value = JsonParser.parseString(json);
    } catch (JsonParseException invalid) {
      throw new IllegalArgumentException("model must contain valid directives JSON. Nothing was changed.");
    }
    if (!value.isJsonObject() || !Set.of(CREATE, UPDATE, DELETE, "project", "projectKey").containsAll(value.getAsJsonObject().keySet())) {
      throw new IllegalArgumentException("Provide a change file with create, update and delete arrays. Nothing was changed.");
    }
    var object = value.getAsJsonObject();
    for (var key : List.of("project", "projectKey")) {
      if (object.has(key) && (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString()
        || !projectKey.equals(object.get(key).getAsString()))) {
        throw new IllegalArgumentException("The directive file belongs to a different project. Nothing was changed.");
      }
    }
    var gson = new Gson();
    var create = new ArrayList<ProjectDirective>();
    for (var item : array(object, CREATE)) {
      create.add(parseDirective(item, gson));
    }
    var update = array(object, UPDATE);
    var delete = array(object, DELETE);
    var ids = new HashSet<String>();
    for (var item : update) {
      addId(item, ids);
    }
    for (var item : delete) {
      if (!item.isJsonObject() || !Set.of("id").containsAll(item.getAsJsonObject().keySet())) {
        throw new IllegalArgumentException("Delete accepts only {id} records. Nothing was changed.");
      }
      addId(item, ids);
    }
    return new Changes(List.copyOf(create), update, delete);
  }

  private static JsonArray array(JsonObject object, String key) {
    if (!object.has(key)) {
      return new JsonArray();
    }
    if (!object.get(key).isJsonArray()) {
      throw new IllegalArgumentException("Directive " + key + " must be an array. Nothing was changed.");
    }
    return object.getAsJsonArray(key);
  }

  private static void addId(JsonElement item, Set<String> ids) {
    if (!item.isJsonObject() || !item.getAsJsonObject().has("id") || !item.getAsJsonObject().get("id").isJsonPrimitive()
      || !item.getAsJsonObject().get("id").getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Each update/delete requires an explicit id. Nothing was changed.");
    }
    var id = item.getAsJsonObject().get("id").getAsString();
    if (!UUID.fromString(id).toString().equalsIgnoreCase(id) || !ids.add(id.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Invalid, repeated or conflicting directive id. Nothing was changed.");
    }
  }

  private static ProjectDirective parseDirective(JsonElement item, Gson gson) {
    if (!item.isJsonObject() || !FIELDS.containsAll(item.getAsJsonObject().keySet())) {
      throw new IllegalArgumentException("Each directive must be an object containing supported fields. Nothing was changed.");
    }
    for (var field : item.getAsJsonObject().entrySet()) {
      if (!field.getValue().isJsonNull() && (!field.getValue().isJsonPrimitive() || !field.getValue().getAsJsonPrimitive().isString())) {
        throw new IllegalArgumentException("Directive fields must be strings.");
      }
    }
    var directive = gson.fromJson(item, ProjectDirective.class).forCreation();
    directive.validate();
    return directive;
  }

  private static Map<ProjectDirective.Identity, List<String>> existingDirectiveKeys(ArchitectureApi api, String projectKey, String projectId) {
    var keys = new HashMap<ProjectDirective.Identity, List<String>>();
    var ids = new HashSet<String>();
    Integer total = null;
    for (var pageIndex = 1; ; pageIndex++) {
      var page = api.getDirectivePage(projectKey, projectId, pageIndex, 100);
      if (total == null) {
        total = page.page().total();
      }
      if (!total.equals(page.page().total())
        || page.projectDirectives().size() != Math.clamp(total - ((long) pageIndex - 1) * 100, 0, 100)
        || ids.size() + page.projectDirectives().size() > total) {
        throw new IllegalStateException("The public directives list changed while reading it. Read it again before changing directives.");
      }
      for (var directive : page.projectDirectives()) {
        if (!ids.add(directive.id().toLowerCase(Locale.ROOT))) {
          throw new IllegalStateException("Inconsistent public directives pages; no complete list was read.");
        }
        keys.computeIfAbsent(directive.identity(), ignored -> new ArrayList<>()).add(directive.id());
      }
      if (ids.size() == total) {
        return keys;
      }
      if (page.projectDirectives().isEmpty()) {
        throw new IllegalStateException("Incomplete public directives pagination.");
      }
    }
  }

  static Applied apply(ArchitectureApi api, String projectKey, String projectId, Changes input, boolean dryRun) {
    var gson = new Gson();
    var changes = new ArrayList<Change>();
    var toDelete = new ArrayList<ProjectDirective>();
    var skipped = 0;
    for (var item : input.update()) {
      var change = prepareChange(api, projectKey, projectId, item, UPDATE, gson);
      if (change.before().withoutIdentity().equals(change.after())) {
        skipped++;
      } else {
        changes.add(change);
      }
    }
    for (var item : input.delete()) {
      toDelete.add(prepareChange(api, projectKey, projectId, item, DELETE, gson).before());
    }
    var moving = changes.stream().filter(change -> !change.before().identity().equals(change.after().identity())).toList();
    var existing = input.create().isEmpty() && moving.isEmpty() ? Map.<ProjectDirective.Identity, List<String>>of() : existingDirectiveKeys(api, projectKey, projectId);
    var known = new HashSet<>(existing.keySet());
    var afterKeys = new HashSet<ProjectDirective.Identity>();
    for (var change : moving) {
      var key = change.after().identity();
      if (known.contains(key) || !afterKeys.add(key)) {
        throw new IllegalArgumentException("An update would duplicate another directive. Nothing was changed.");
      }
    }
    var noVisibleDirectives = !input.create().isEmpty() && known.isEmpty();
    var creations = planCreates(input.create(), changes, toDelete, known);
    if (existing.values().stream().mapToInt(List::size).sum() + creations.items().size() > MAX_DIRECTIVES_PER_PROJECT) {
      throw new IllegalArgumentException("The batch would exceed the 1000 directives per project limit. Nothing was changed.");
    }
    var skippedIds = input.create().stream().flatMap(item -> existing.getOrDefault(item.identity(), List.of()).stream()).distinct().toList();
    var result = new Applied(projectKey, dryRun ? "dry-run" : "applied", creations.items(), List.copyOf(changes), List.copyOf(toDelete), skipped + creations.skipped(), skippedIds,
      new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
      warnings(noVisibleDirectives));
    if (!dryRun) {
      writeChanges(api, projectKey, projectId, result);
    }
    return result;
  }

  private static List<String> warnings(boolean noVisibleDirectives) {
    var warnings = new ArrayList<String>();
    warnings.add("Concurrent edits can race the reads; the public API has no conditional writes. Changes are applied individually without automatic retry or rollback.");
    if (noVisibleDirectives) {
      warnings.add("No directives are visible. Check project access: duplicate checks cannot identify directives hidden by permissions.");
    }
    return List.copyOf(warnings);
  }

  private static CreatePlan planCreates(List<ProjectDirective> input, List<Change> changes, List<ProjectDirective> toDelete,
    Set<ProjectDirective.Identity> known) {
    var skipped = 0;
    var touched = new HashSet<ProjectDirective.Identity>();
    for (var change : changes) {
      touched.add(change.before().identity()); touched.add(change.after().identity());
    }
    for (var item : toDelete) {
      touched.add(item.identity());
    }
    var toCreate = new ArrayList<ProjectDirective>();
    for (var item : input) {
      if (touched.contains(item.identity())) {
        throw new IllegalArgumentException("Create conflicts with an update or deletion of the same directive operation. Nothing was changed.");
      }
      if (known.add(item.identity())) {
        toCreate.add(item);
      } else {
        skipped++;
      }
    }
    return new CreatePlan(List.copyOf(toCreate), skipped);
  }

  private static void writeChanges(ArchitectureApi api, String projectKey, String projectId, Applied result) {
    var jobs = new ArrayList<Write>();
    for (var item : result.toCreate()) {
      jobs.add(new Write(CREATE, item));
    }
    for (var item : result.changes()) {
      jobs.add(new Write(UPDATE, new ProjectDirective(item.id(), null, item.after().type(), item.after().ecosystem(),
        item.after().graphType(), item.after().fromNodeKey(), item.after().toNodeKey(), item.after().graphPerspective(), item.after().description(), item.before().origin())));
    }
    for (var item : result.toDelete()) {
      jobs.add(new Write(DELETE, item));
    }
    for (var job : jobs) {
      try {
        writeChange(api, projectKey, projectId, result, job);
      } catch (RuntimeException failure) {
        var confirmed = result.created().size() + result.updated().size() + result.deleted().size();
        if (confirmed == 0 && isDefiniteRejection(failure)) {
          throw failure;
        }
        throw stoppedChanges(result, job, failure, confirmed);
      }
    }
  }

  private static void writeChange(ArchitectureApi api, String projectKey, String projectId, Applied result, Write job) {
    switch (job.operation()) {
      case CREATE -> result.created().add(api.createDirective(projectKey, projectId, job.directive()));
      case UPDATE -> result.updated().add(api.updateDirective(projectKey, projectId, job.directive().id(), job.directive()));
      case DELETE -> {
        api.deleteDirective(projectKey, job.directive().id());
        result.deleted().add(job.directive().id());
      }
      default -> throw new IllegalStateException("Unknown directive write.");
    }
  }

  private static IllegalStateException stoppedChanges(Applied result, Write job, RuntimeException failure, int confirmed) {
    return new IllegalStateException("Directive changes stopped after " + confirmed + " confirmed writes. Created ids: "
      + result.created().stream().map(ProjectDirective::id).toList() + ". Updated ids: " + result.updated().stream().map(ProjectDirective::id).toList()
      + ". Deleted ids: " + result.deleted()
      + (isDefiniteRejection(failure) ? ". The server rejected the next directive. " : ". The attempted change may already have been applied. ")
      + (job.directive().id() == null ? "Inspect the current list before retrying. " : ("Inspect directive " + job.directive().id() + " before retrying. "))
      + failure.getMessage(), failure);
  }

  private static Change prepareChange(ArchitectureApi api, String projectKey, String projectId, JsonElement item, String operation, Gson gson) {
    if (!item.isJsonObject() || !FIELDS.containsAll(item.getAsJsonObject().keySet())) {
      throw new IllegalArgumentException("Each directive must be an object containing supported fields.");
    }
    var object = item.getAsJsonObject();
    if (!object.has("id") || !object.get("id").isJsonPrimitive() || !object.get("id").getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Each directive requires an explicit id.");
    }
    var id = object.get("id").getAsString();
    if (!UUID.fromString(id).toString().equalsIgnoreCase(id)) {
      throw new IllegalArgumentException("Directive id must be a UUID.");
    }
    if (DELETE.equals(operation) && !Set.of("id").containsAll(object.keySet())) {
      throw new IllegalArgumentException("Delete accepts only {id} records; it never deletes omitted directives.");
    }
    var before = api.getDirective(projectKey, projectId, id);
    if (DELETE.equals(operation)) {
      return new Change(id, before, before.withoutIdentity());
    }
    var merged = gson.toJsonTree(before.withoutIdentity()).getAsJsonObject();
    mergeUpdateFields(object, merged);
    var after = gson.fromJson(merged, ProjectDirective.class);
    after.validate();
    return new Change(id, before, after);
  }

  private static void mergeUpdateFields(JsonObject object, JsonObject merged) {
    for (var field : object.entrySet()) {
      if (Set.of("id", "projectId", "origin").contains(field.getKey())) {
        continue;
      }
      if (field.getValue().isJsonNull()) {
        throw new IllegalArgumentException("Directive fields cannot be cleared with null by the public API. Nothing was changed.");
      }
      if (!field.getValue().isJsonPrimitive() || !field.getValue().getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("Directive fields must be strings.");
      }
      merged.add(field.getKey(), field.getValue());
    }
  }

  private static boolean isDefiniteRejection(RuntimeException failure) {
    return (failure instanceof ClientErrorException clientError && clientError.status() != java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT)
      || failure instanceof ForbiddenException || failure instanceof NotFoundException || failure instanceof UnauthorizedException;
  }

  private record CreatePlan(List<ProjectDirective> items, int skipped) {
  }

  record Changes(List<ProjectDirective> create, JsonArray update, JsonArray delete) {
  }

  record Change(String id, ProjectDirective before, ProjectDirective after) {
  }

  private record Write(String operation, ProjectDirective directive) {
  }

  record Applied(String projectKey, String mode, List<ProjectDirective> toCreate, List<Change> changes, List<ProjectDirective> toDelete, int skipped, List<String> skippedIds,
    List<ProjectDirective> created, List<ProjectDirective> updated, List<String> deleted, List<String> warnings) {
  }
}
