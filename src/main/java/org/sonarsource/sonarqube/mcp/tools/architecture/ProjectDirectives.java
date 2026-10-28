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
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

final class ProjectDirectives {
  private static final int MAX_DIRECTIVES_PER_PROJECT = 1000;
  private static final String CREATE = "create";
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
    if (!value.isJsonObject() || !Set.of(CREATE, "project", "projectKey").containsAll(value.getAsJsonObject().keySet())) {
      throw new IllegalArgumentException("Provide a change file with a create array. Nothing was changed.");
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
    return new Changes(List.copyOf(create));
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
    var existing = input.create().isEmpty() ? Map.<ProjectDirective.Identity, List<String>>of() : existingDirectiveKeys(api, projectKey, projectId);
    var known = new HashSet<>(existing.keySet());
    var noVisibleDirectives = !input.create().isEmpty() && known.isEmpty();
    var creations = planCreates(input.create(), known);
    if (existing.values().stream().mapToInt(List::size).sum() + creations.items().size() > MAX_DIRECTIVES_PER_PROJECT) {
      throw new IllegalArgumentException("The batch would exceed the 1000 directives per project limit. Nothing was changed.");
    }
    var skippedIds = input.create().stream().flatMap(item -> existing.getOrDefault(item.identity(), List.of()).stream()).distinct().toList();
    var result = new Applied(projectKey, dryRun ? "dry-run" : "applied", creations.items(), creations.skipped(), skippedIds, new ArrayList<>(),
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

  private static CreatePlan planCreates(List<ProjectDirective> input, Set<ProjectDirective.Identity> known) {
    var skipped = 0;
    var toCreate = new ArrayList<ProjectDirective>();
    for (var item : input) {
      if (known.add(item.identity())) {
        toCreate.add(item);
      } else {
        skipped++;
      }
    }
    return new CreatePlan(List.copyOf(toCreate), skipped);
  }

  private static void writeChanges(ArchitectureApi api, String projectKey, String projectId, Applied result) {
    for (var directive : result.toCreate()) {
      try {
        result.created().add(api.createDirective(projectKey, projectId, directive));
      } catch (RuntimeException failure) {
        var confirmed = result.created().size();
        if (confirmed == 0 && isDefiniteRejection(failure)) {
          throw failure;
        }
        throw new IllegalStateException("Directive changes stopped after " + confirmed + " confirmed writes. Created ids: "
          + result.created().stream().map(ProjectDirective::id).toList()
          + (isDefiniteRejection(failure) ? ". The server rejected the next directive. " : ". The attempted change may already have been applied. ")
          + "Inspect the current list before retrying. " + failure.getMessage(), failure);
      }
    }
  }









  private static boolean isDefiniteRejection(RuntimeException failure) {
    return (failure instanceof ClientErrorException clientError && clientError.status() != java.net.HttpURLConnection.HTTP_CLIENT_TIMEOUT)
      || failure instanceof ForbiddenException || failure instanceof NotFoundException || failure instanceof UnauthorizedException;
  }

  private record CreatePlan(List<ProjectDirective> items, int skipped) {
  }

  record Changes(List<ProjectDirective> create) {
  }





  record Applied(String projectKey, String mode, List<ProjectDirective> toCreate, int skipped, List<String> skippedIds,
    List<ProjectDirective> created, List<String> warnings) {
  }
}
