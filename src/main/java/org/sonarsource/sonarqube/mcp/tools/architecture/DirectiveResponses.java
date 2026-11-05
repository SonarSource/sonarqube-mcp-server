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

  static Response of(ProjectDirectives.Applied result) {
    var preview = "dry-run".equals(result.mode());
    return new Response(result.projectKey(), result.mode(), preview ? "preview" : "receipt",
      preview ? result.toCreate() : null,
      preview ? result.changes().stream().map(DirectiveResponses::change).toList() : null,
      preview ? result.toDelete().stream().map(DirectiveResponses::summary).toList() : null,
      result.skipped(), result.skippedIds(), result.created().stream().map(DirectiveResponses::summary).toList(),
      result.updated().stream().map(DirectiveResponses::summary).toList(), result.deleted(), result.warnings());
  }

  private static Change change(ProjectDirectives.Change change) {
    var gson = new Gson();
    var before = gson.toJsonTree(change.before().withoutIdentity()).getAsJsonObject();
    var after = gson.toJsonTree(change.after()).getAsJsonObject();
    var fields = new LinkedHashMap<String, String>();
    for (var field : after.entrySet()) {
      if (!field.getValue().equals(before.get(field.getKey()))) {
        fields.put(field.getKey(), field.getValue().getAsString());
      }
    }
    return new Change(change.id(), summary(change.before()), fields);
  }

  record Summary(@Nullable String id, String type, String ecosystem, String graphType, String fromNodeKey, String toNodeKey,
    @Nullable String graphPerspective, @Nullable String origin, boolean descriptionOmitted) {
  }

  record Change(String id, Summary before, Map<String, String> after) {
  }

  record Response(String projectKey, String mode, String view, @Nullable List<ProjectDirective> toCreate, @Nullable List<Change> changes,
    @Nullable List<Summary> toDelete, int skipped, List<String> skippedIds, List<Summary> created, List<Summary> updated, List<String> deleted,
    List<String> warnings) {
  }
}
