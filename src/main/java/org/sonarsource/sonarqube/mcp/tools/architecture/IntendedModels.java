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
      var models = element.isJsonObject() && element.getAsJsonObject().has("models") ? element.getAsJsonObject().get("models") : element;
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
      throw new IllegalArgumentException("model must be a JSON array with one intended architecture per language, as architecture_get with target intended returns it "
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
        if (object.has("models") && object.has(property) && !object.get(property).isJsonNull()) {
          throw new JsonParseException("partial architecture view; read the whole model without component, depth or language first");
        }
      }
      var partial = object.get("partial");
      if ((partial != null && !partial.isJsonNull()
        && (!partial.isJsonPrimitive() || !partial.getAsJsonPrimitive().isBoolean() || partial.getAsBoolean()))
        || (object.has("hiddenSubgroups") && !object.get("hiddenSubgroups").isJsonNull())) {
        throw new JsonParseException("partial architecture view; read the whole model without component, depth or language first");
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
      var groups = requireArray(model, "groups", true);
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
      if (++count[0] > MAX_GROUPS) {
        throw new JsonParseException("groups exceed the client count limit of " + MAX_GROUPS);
      }
      requireObject(group);
      requireString(group, "label");
      var object = group.getAsJsonObject();
      if (object.has("patterns")) {
        for (var pattern : requireArray(group, "patterns", false).getAsJsonArray()) {
          if (!pattern.isJsonPrimitive() || !pattern.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("patterns must contain strings");
          }
        }
      }
      for (var property : List.of("interface", "interfaceAccess")) {
        if (object.has(property) && (!object.get(property).isJsonPrimitive() || !object.get(property).getAsJsonPrimitive().isBoolean())) {
          throw new JsonParseException(property + " must be a boolean");
        }
      }
      if (object.has("groups")) {
        validateGroups(requireArray(group, "groups", false), depth + 1, count);
      }
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
    var languages = new HashSet<String>();
    var groupCount = new int[] {0};
    for (var model : models) {
      var language = model.language();
      if (language == null || !ArchitectureApi.LANGUAGES.contains(language)) {
        problems.add("Language '" + language + "' is not one of " + String.join(", ", ArchitectureApi.LANGUAGES) + ".");
        continue;
      }
      if (!languages.add(language)) {
        problems.add("Language " + language + " appears more than once.");
      }
      if (model.type() == null || !TYPES.contains(model.type())) {
        problems.add("Type '" + model.type() + "' of " + language + " is not file or namespace.");
      }
      var groupKeys = new HashSet<String>();
      checkGroups(language, nullToEmpty(model.groups()), null, 1, groupKeys, groupCount, problems);
      checkConstraints(language, nullToEmpty(model.constraints()), problems);
    }
    if (groupCount[0] > MAX_GROUPS) {
      problems.add("The model has more than " + MAX_GROUPS + " groups.");
    }
    return problems;
  }

  private static void checkGroups(String language, List<IntendedArchitecture.Group> groups, @Nullable String parentKey, int depth, Set<String> keys,
    int[] groupCount, List<String> problems) {
    if (depth > MAX_DEPTH) {
      problems.add("Groups of " + language + " are nested more than " + MAX_DEPTH + " levels deep.");
      return;
    }
    for (var group : groups) {
      groupCount[0]++;
      if (group == null || group.label() == null || group.label().isBlank()) {
        problems.add("A group of " + language + (parentKey == null ? "" : " under '" + parentKey + "'") + " has no label.");
        continue;
      }
      var key = keyOf(parentKey, group.label());
      if (!keys.add(key)) {
        problems.add("Group '" + key + "' of " + language + " appears more than once.");
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
        problems.add("Constraint " + describe(constraint) + " of " + language + " appears more than once.");
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
    before.forEach((language, model) -> {
      var other = after.get(language);
      if (other == null) {
        removed.add(language + " language");
      }
      model.groups().keySet().stream().filter(key -> other == null || !other.groups().containsKey(key))
        .forEach(key -> removed.add(language + " group " + key));
      model.constraints().stream().filter(constraint -> other == null || !other.constraints().contains(constraint))
        .forEach(constraint -> removed.add(language + " constraint " + describe(constraint)));
    });
    after.forEach((language, model) -> {
      var other = before.get(language);
      if (other == null) {
        added.add(language + " language");
      } else if (!other.groups().keySet().stream().filter(model.groups()::containsKey).toList()
        .equals(model.groups().keySet().stream().filter(other.groups()::containsKey).toList())) {
        changed.add(language + " group order");
      }
      if (other != null && !Objects.equals(other.type(), model.type())) {
        changed.add(language + " type " + other.type() + " -> " + model.type());
      }
      model.groups().forEach((key, group) -> {
        var previous = other == null ? null : other.groups().get(key);
        if (previous == null) {
          added.add(language + " group " + key);
        } else {
          if (!previous.path().equals(group.path())) {
            changed.add(language + " group " + key + " hierarchy " + previous.path() + " -> " + group.path());
          }
          if (!previous.patterns().equals(group.patterns()) || previous.isInterface() != group.isInterface()
            || previous.interfaceAccess() != group.interfaceAccess()) {
            changed.add(language + " group " + key + " " + previous + " -> " + group);
          }
        }
      });
      model.constraints().stream().filter(constraint -> other == null || !other.constraints().contains(constraint))
        .forEach(constraint -> added.add(language + " constraint " + describe(constraint)));
    });
    return new Diff(removed, added, changed);
  }

  private record IndexedModel(@Nullable String type, Map<String, GroupSettings> groups, Set<IntendedArchitecture.Constraint> constraints) {
  }

  /**
   * Keeps label segments distinct from path separators, with absent flags read as false and patterns as an unordered set.
   */
  private record GroupSettings(List<String> path, Set<String> patterns, boolean isInterface, boolean interfaceAccess) {
    @Override
    public String toString() {
      return "patterns " + patterns + (isInterface ? ", interface" : "") + (interfaceAccess ? ", interfaceAccess" : "");
    }
  }

  private static Map<String, IndexedModel> index(List<IntendedArchitecture> models) {
    var indexed = new LinkedHashMap<String, IndexedModel>();
    for (var model : models) {
      var groups = new LinkedHashMap<String, GroupSettings>();
      indexGroups(nullToEmpty(model.groups()), List.of(), groups);
      var constraints = new LinkedHashSet<>(nullToEmpty(model.constraints()));
      indexed.put(model.language(), new IndexedModel(model.type(), groups, constraints));
    }
    return indexed;
  }

  private static void indexGroups(List<IntendedArchitecture.Group> groups, List<String> parentPath, Map<String, GroupSettings> index) {
    for (var group : groups) {
      var path = new ArrayList<>(parentPath);
      path.add(group.label());
      var key = String.join("/", path);
      index.put(key, new GroupSettings(List.copyOf(path), new TreeSet<>(nullToEmpty(group.patterns())), Boolean.TRUE.equals(group.isInterface()),
        Boolean.TRUE.equals(group.interfaceAccess())));
      indexGroups(nullToEmpty(group.groups()), path, index);
    }
  }

  private static String keyOf(@Nullable String parentKey, String label) {
    return parentKey == null ? label : (parentKey + "/" + label);
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
