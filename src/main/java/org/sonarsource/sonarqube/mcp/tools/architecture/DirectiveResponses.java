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
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;

final class DirectiveResponses {
  private DirectiveResponses() {
  }

  static Summary summary(ProjectDirective directive) {
    return new Summary(directive.id(), directive.type(), directive.ecosystem(), directive.graphType(), directive.fromNodeKey(),
      directive.toNodeKey(), directive.graphPerspective(), directive.origin(), directive.description() != null);
  }

  static Record of(ProjectDirectives.Applied result) {
    if ("dry-run".equals(result.mode())) {
      return new Preview(result.projectKey(), result.mode(), result.toCreate(),
        result.changes().stream().map(DirectiveResponses::change).toList(),
        result.toDelete().stream().map(DirectiveResponses::target).toList(), result.skipped(), result.skippedIds(), result.warnings());
    }
    return new Confirmation(result.projectKey(), result.mode(),
      new Counts(result.created().size(), result.updated().size(), result.deleted().size(), result.skipped()),
      result.created().stream().map(ProjectDirective::id).toList(), result.updated().stream().map(ProjectDirective::id).toList(),
      result.deleted(), result.skippedIds(), result.warnings());
  }

  private static Target target(ProjectDirective directive) {
    return new Target(directive.id(), directive.type(), directive.ecosystem(), directive.graphType(), directive.fromNodeKey(),
      directive.toNodeKey(), directive.graphPerspective());
  }

  private static Change change(ProjectDirectives.Change change) {
    var gson = new Gson();
    var before = gson.toJsonTree(change.before().withoutIdentity()).getAsJsonObject();
    var after = gson.toJsonTree(change.after()).getAsJsonObject();
    var previous = new LinkedHashMap<String, String>();
    var proposed = new LinkedHashMap<String, String>();
    for (var field : after.entrySet()) {
      var oldValue = before.get(field.getKey());
      if (!field.getValue().equals(oldValue)) {
        previous.put(field.getKey(), oldValue == null || oldValue.isJsonNull() ? null : oldValue.getAsString());
        proposed.put(field.getKey(), field.getValue().getAsString());
      }
    }
    return new Change(change.id(), previous, proposed);
  }

  record Summary(@Nullable String id, String type, String ecosystem, String graphType, String fromNodeKey, String toNodeKey,
    @Nullable String graphPerspective, @Nullable String origin, boolean descriptionOmitted) {
  }

  record Target(String id, String type, String ecosystem, String graphType, String fromNodeKey, String toNodeKey,
    @Nullable String graphPerspective) {
  }

  record Change(String id, Map<String, String> before, Map<String, String> after) {
  }

  record Preview(String projectKey, String mode, List<ProjectDirective> toCreate, List<Change> changes, List<Target> toDelete,
    int skipped, List<String> skippedIds, List<String> warnings) {
  }

  record Counts(int created, int updated, int deleted, int skipped) {
  }

  record Confirmation(String projectKey, String mode, Counts counts, List<String> createdIds, List<String> updatedIds,
    List<String> deletedIds, List<String> skippedIds, List<String> warnings) {
  }
}
