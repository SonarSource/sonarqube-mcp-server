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
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureSetToolResponse.Diff;

/**
 * Checks and compares intended architectures in the shape {@code architecture_get with target intended} returns: one entry per
 * language, groups keyed by their labels joined with {@code /}.
 */
final class IntendedModels {

  private static final String MODELS_PROPERTY = "models";
  private static final String GROUPS_PROPERTY = "groups";
  private static final String DUPLICATE_SUFFIX = " appears more than once.";
  private static final String GROUP_DESCRIPTION = " group ";
  private static final int MAX_REPORTED_PROBLEMS = 10;
  private static final int MAX_DEPTH = 64;
  private static final int MAX_GROUPS = 50_000;
  private static final Set<String> TYPES = Set.of("file", "namespace");

  private IntendedModels() {
    // static only
  }

  static List<IntendedArchitecture> parse(String json) {
    try {
      var element = JsonParser.parseString(json);
      var models = element.isJsonObject() && element.getAsJsonObject().has(MODELS_PROPERTY) ? element.getAsJsonObject().get(MODELS_PROPERTY) : element;
      if (!models.isJsonArray()) {
        throw new JsonParseException("expected an array");
      }
      validateShape(models);
      rejectPartialView(element);
      var parsed = Arrays.asList(new Gson().fromJson(models, IntendedArchitecture[].class));
      if (parsed.contains(null)) {
        throw new JsonParseException("an entry is null");
      }
      return List.copyOf(parsed);
    } catch (JsonParseException e) {
      throw new IllegalArgumentException("model must be a JSON array of intended architecture perspectives, as architecture_get with target intended returns them "
        + "(" + e.getMessage() + "). Nothing was changed.");
    }
  }

  @Nullable
  static String embeddedRevision(String json) {
    var element = JsonParser.parseString(json);
    if (!element.isJsonObject() || !element.getAsJsonObject().has("revision")) {
      return null;
    }
    var revision = element.getAsJsonObject().get("revision");
    if (!revision.isJsonPrimitive() || !revision.getAsJsonPrimitive().isString() || revision.getAsString().isBlank()) {
      throw new IllegalArgumentException("The model's revision must be a nonblank string. Nothing was changed.");
    }
    return revision.getAsString();
  }

  private static void rejectPartialView(JsonElement element) {
    if (element.isJsonArray()) {
      element.getAsJsonArray().forEach(IntendedModels::rejectPartialView);
    } else if (element.isJsonObject()) {
      var object = element.getAsJsonObject();
      for (var property : List.of("component", "depth", "language")) {
        if (object.has(MODELS_PROPERTY) && object.has(property) && !object.get(property).isJsonNull()) {
          throw new IllegalArgumentException("This is a partial architecture view. Read the complete model without component, depth or language before editing it. Nothing was changed.");
        }
      }
      var partial = object.get("partial");
      if ((partial != null && !partial.isJsonNull()
        && (!partial.isJsonPrimitive() || !partial.getAsJsonPrimitive().isBoolean() || partial.getAsBoolean()))
        || (object.has("hiddenSubgroups") && !object.get("hiddenSubgroups").isJsonNull())) {
        throw new IllegalArgumentException("This is a partial architecture view. Read the complete model without component, depth or language before editing it. Nothing was changed.");
      }
      object.entrySet().forEach(entry -> rejectPartialView(entry.getValue()));
    }
  }

  private static void validateShape(JsonElement models) {
    var count = new int[] {0};
    for (var model : models.getAsJsonArray()) {
      requireObject(model);
      requireString(model, "type");
      requireString(model, "language");
      optionalString(model.getAsJsonObject(), "label");
      optionalString(model.getAsJsonObject(), "description");
      var groups = requireArray(model, GROUPS_PROPERTY, true);
      validateGroups(groups, 1, count);
      for (var constraint : requireArray(model, "constraints", true).getAsJsonArray()) {
        requireObject(constraint);
        requireString(constraint, "from");
        requireString(constraint, "to");
      }
    }
  }

  private static void validateGroups(JsonElement groups, int depth, int[] count) {
    if (depth > MAX_DEPTH) {
      throw new JsonParseException("groups exceed the client depth limit of " + MAX_DEPTH);
    }
    for (var group : groups.getAsJsonArray()) {
      count[0]++;
      if (count[0] > MAX_GROUPS) {
        throw new JsonParseException("groups exceed the client count limit of " + MAX_GROUPS);
      }
      validateGroup(group, depth, count);
    }
  }

  private static void validateGroup(JsonElement group, int depth, int[] count) {
    requireObject(group);
    requireString(group, "label");
    var object = group.getAsJsonObject();
    optionalString(object, "description");
    optionalString(object, "patternId");
    if (object.has("patternId") && !object.get("patternId").isJsonNull()) {
      var id = object.get("patternId").getAsString();
      if (!java.util.UUID.fromString(id).toString().equalsIgnoreCase(id)) {
        throw new JsonParseException("patternId must be a UUID");
      }
    }
    if (object.has("patterns") && !object.get("patterns").isJsonNull()) {
      for (var pattern : requireArray(group, "patterns", false).getAsJsonArray()) {
        if (!pattern.isJsonPrimitive() || !pattern.getAsJsonPrimitive().isString()) {
          throw new JsonParseException("patterns must contain strings");
        }
      }
    }
    for (var property : List.of("interface", "interfaceAccess")) {
      if (object.has(property) && !object.get(property).isJsonNull() && (!object.get(property).isJsonPrimitive() || !object.get(property).getAsJsonPrimitive().isBoolean())) {
        throw new JsonParseException(property + " must be a boolean");
      }
    }
    if (object.has(GROUPS_PROPERTY) && !object.get(GROUPS_PROPERTY).isJsonNull()) {
      validateGroups(requireArray(group, GROUPS_PROPERTY, false), depth + 1, count);
    }
  }

  private static void optionalString(com.google.gson.JsonObject object, String property) {
    var value = object.get(property);
    if (value != null && !value.isJsonNull() && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
      throw new JsonParseException(property + " must be a string");
    }
  }

  private static void requireObject(JsonElement element) {
    if (!element.isJsonObject()) {
      throw new JsonParseException("model entries, groups and constraints must be objects");
    }
  }

  private static void requireString(JsonElement element, String property) {
    var value = element.getAsJsonObject().get(property);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new JsonParseException(property + " must be a string");
    }
  }

  private static JsonElement requireArray(JsonElement element, String property, boolean required) {
    var value = element.getAsJsonObject().get(property);
    if (value == null || !value.isJsonArray()) {
      throw new JsonParseException(property + (required ? " is required and" : "") + " must be an array");
    }
    return value;
  }

  /**
   * Message listing why the models cannot be saved, or null when they can.
   */
  @Nullable
  static String invalidReason(List<IntendedArchitecture> models) {
    var problems = problems(models);
    if (problems.isEmpty()) {
      return null;
    }
    var lines = new ArrayList<String>();
    lines.add("The model is invalid, so nothing was changed:");
    problems.stream().limit(MAX_REPORTED_PROBLEMS).forEach(problem -> lines.add("- " + problem));
    if (problems.size() > MAX_REPORTED_PROBLEMS) {
      lines.add("- and " + (problems.size() - MAX_REPORTED_PROBLEMS) + " more.");
    }
    return String.join("\n", lines);
  }

  private static List<String> problems(List<IntendedArchitecture> models) {
    var problems = new ArrayList<String>();
    if (models.isEmpty()) {
      problems.add("The model has no language. Give at least one.");
    }
    var perspectives = new HashSet<List<String>>();
    var groupCount = new int[] {0};
    for (var model : models) {
      var language = model.language();
      if (language == null || !ArchitectureApi.LANGUAGES.contains(language)) {
        problems.add("Language '" + language + "' is not one of " + String.join(", ", ArchitectureApi.LANGUAGES) + ".");
        continue;
      }
      if (model.type() == null || !TYPES.contains(model.type())) {
        problems.add("Type '" + model.type() + "' of " + language + " is not file or namespace.");
      }
      if (!perspectives.add(Arrays.asList(language, model.type()))) {
        problems.add("Perspective " + language + "/" + model.type() + DUPLICATE_SUFFIX);
      }
      var groupKeys = new HashSet<List<String>>();
      checkGroups(language, nullToEmpty(model.groups()), List.of(), 1, groupKeys, groupCount, problems);
      checkConstraints(language, nullToEmpty(model.constraints()), problems);
    }
    if (models.size() > ArchitectureApi.MAX_PERSPECTIVES) {
      problems.add("The model has more than " + ArchitectureApi.MAX_PERSPECTIVES + " perspectives.");
    }
    if (groupCount[0] > MAX_GROUPS) {
      problems.add("The model has more than " + MAX_GROUPS + " groups.");
    }
    return problems;
  }

  private static void checkGroups(String language, List<IntendedArchitecture.Group> groups, List<String> parentPath, int depth, Set<List<String>> keys,
    int[] groupCount, List<String> problems) {
    if (depth > MAX_DEPTH) {
      problems.add("Groups of " + language + " are nested more than " + MAX_DEPTH + " levels deep.");
      return;
    }
    for (var group : groups) {
      groupCount[0]++;
      if (group == null || group.label() == null || group.label().isBlank()) {
        problems.add("A group of " + language + (parentPath.isEmpty() ? "" : (" under '" + String.join("/", parentPath) + "'")) + " has no label.");
        continue;
      }
      var key = new ArrayList<>(parentPath);
      key.add(group.label());
      if (!keys.add(key)) {
        problems.add("Group '" + key + "' of " + language + DUPLICATE_SUFFIX);
      }
      checkGroups(language, nullToEmpty(group.groups()), key, depth + 1, keys, groupCount, problems);
    }
  }

  private static void checkConstraints(String language, List<IntendedArchitecture.Constraint> constraints, List<String> problems) {
    var seen = new HashSet<IntendedArchitecture.Constraint>();
    for (var constraint : constraints) {
      if (constraint == null) {
        problems.add("A constraint of " + language + " is null.");
      } else if (constraint.from() == null || constraint.to() == null) {
        problems.add("Constraint endpoints of " + language + " must be strings.");
      } else if (!seen.add(constraint)) {
        problems.add("Constraint " + describe(constraint) + " of " + language + DUPLICATE_SUFFIX);
      }
    }
  }

  /**
   * What applying {@code next} changes in {@code current}, removals first.
   */
  static Diff diff(List<IntendedArchitecture> current, List<IntendedArchitecture> next) {
    var before = index(current);
    var after = index(next);
    var removed = new ArrayList<String>();
    var added = new ArrayList<String>();
    var changed = new ArrayList<String>();
    before.forEach((language, model) -> collectRemoved(perspectiveLabel(language, before, after), model, after.get(language), removed));
    after.forEach((language, model) ->
      collectAddedAndChanged(perspectiveLabel(language, before, after), model, before.get(language), added, changed));
    return new Diff(removed, added, changed);
  }

  private static String perspectiveLabel(List<String> perspective, Map<List<String>, IndexedModel> before,
    Map<List<String>, IndexedModel> after) {
    var qualifiers = java.util.stream.Stream.concat(before.keySet().stream(), after.keySet().stream())
      .filter(key -> key.getFirst().equals(perspective.getFirst())).map(key -> key.get(1)).distinct().count();
    return qualifiers > 1 ? String.join("/", perspective) : perspective.getFirst();
  }

  private static void collectRemoved(String language, IndexedModel model, @Nullable IndexedModel other, List<String> removed) {
    if (other == null) {
      removed.add(language + " language");
    }
    model.groups().keySet().stream().filter(key -> other == null || !other.groups().containsKey(key))
      .forEach(key -> removed.add(language + GROUP_DESCRIPTION + String.join("/", key)));
    model.constraints().stream().filter(constraint -> other == null || !other.constraints().contains(constraint))
      .forEach(constraint -> removed.add(language + " constraint " + describe(constraint)));
  }

  private static void collectAddedAndChanged(String language, IndexedModel model, @Nullable IndexedModel other,
    List<String> added, List<String> changed) {
    if (other == null) {
      added.add(language + " language");
    } else if (!other.groups().keySet().stream().filter(model.groups()::containsKey).toList()
      .equals(model.groups().keySet().stream().filter(other.groups()::containsKey).toList())) {
      changed.add(language + " group order");
    }
    if (other != null && !Objects.equals(other.type(), model.type())) {
      changed.add(language + " type " + other.type() + " -> " + model.type());
    }
    if (other != null && (!Objects.equals(other.label(), model.label()) || !Objects.equals(other.description(), model.description()))) {
      changed.add(language + " perspective metadata");
    }
    model.groups().forEach((key, group) -> collectGroupChanges(language, key, group,
      other == null ? null : other.groups().get(key), added, changed));
    model.constraints().stream().filter(constraint -> other == null || !other.constraints().contains(constraint))
      .forEach(constraint -> added.add(language + " constraint " + describe(constraint)));
  }

  private static void collectGroupChanges(String language, List<String> key, GroupSettings group, @Nullable GroupSettings previous,
    List<String> added, List<String> changed) {
    if (previous == null) {
      added.add(language + GROUP_DESCRIPTION + String.join("/", key));
      return;
    }
    if (!previous.patterns().equals(group.patterns()) || previous.isInterface() != group.isInterface()
      || previous.interfaceAccess() != group.interfaceAccess() || !Objects.equals(previous.description(), group.description())
      || !Objects.equals(previous.patternId(), group.patternId())) {
      changed.add(language + GROUP_DESCRIPTION + String.join("/", key) + " " + previous + " -> " + group);
    }
  }

  private record IndexedModel(@Nullable String type, Map<List<String>, GroupSettings> groups, Set<IntendedArchitecture.Constraint> constraints, @Nullable String label, @Nullable String description) {
  }

  /**
   * Keeps label segments distinct from path separators, with absent flags read as false and patterns as an unordered set.
   */
  private record GroupSettings(Set<String> patterns, boolean isInterface, boolean interfaceAccess, @Nullable String description, @Nullable String patternId) {
    @Override
    public String toString() {
      return "patterns " + patterns + (isInterface ? ", interface" : "") + (interfaceAccess ? ", interfaceAccess" : "") + (description == null ? "" : ", description " + description) + (patternId == null ? "" : ", patternId " + patternId);
    }
  }

  private static Map<List<String>, IndexedModel> index(List<IntendedArchitecture> models) {
    var indexed = new LinkedHashMap<List<String>, IndexedModel>();
    for (var model : models) {
      var groups = new LinkedHashMap<List<String>, GroupSettings>();
      indexGroups(nullToEmpty(model.groups()), List.of(), groups);
      var constraints = new LinkedHashSet<>(nullToEmpty(model.constraints()));
      indexed.put(Arrays.asList(model.language(), model.type()), new IndexedModel(model.type(), groups, constraints, model.label(), model.description()));
    }
    return indexed;
  }

  private static void indexGroups(List<IntendedArchitecture.Group> groups, List<String> parentPath, Map<List<String>, GroupSettings> index) {
    for (var group : groups) {
      var path = new ArrayList<>(parentPath);
      path.add(group.label());
      index.put(List.copyOf(path), new GroupSettings(new TreeSet<>(nullToEmpty(group.patterns())), Boolean.TRUE.equals(group.isInterface()),
        Boolean.TRUE.equals(group.interfaceAccess()), group.description(), group.patternId() == null ? null : group.patternId().toLowerCase(java.util.Locale.ROOT)));
      indexGroups(nullToEmpty(group.groups()), path, index);
    }
  }

  private static String describe(IntendedArchitecture.Constraint constraint) {
    return describeEndpoint(constraint.from()) + " -> " + describeEndpoint(constraint.to());
  }

  private static String describeEndpoint(@Nullable String value) {
    return value != null && value.contains(" -> ") ? new JsonPrimitive(value).toString() : String.valueOf(value);
  }

  private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
    return list == null ? List.of() : list;
  }

}
