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
package org.sonarsource.sonarqube.mcp.serverapi.architecture.response;

import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

public record ProjectDirective(@Nullable String id, @Nullable String projectId, String type, String ecosystem, String graphType,
  String fromNodeKey, String toNodeKey, @Nullable String graphPerspective, @Nullable String description, @Nullable String origin) {

  public Identity identity() {
    return new Identity(type, ecosystem, graphType, fromNodeKey, toNodeKey, graphPerspective == null ? "" : graphPerspective);
  }

  public record Identity(String type, String ecosystem, String graphType, String fromNodeKey, String toNodeKey, String perspective) {
  }

  public ProjectDirective withoutIdentity() {
    return new ProjectDirective(null, null, type, ecosystem, graphType, fromNodeKey, toNodeKey, graphPerspective, description, null);
  }

  public ProjectDirective forProject(String project) {
    return new ProjectDirective(null, project, type, ecosystem, graphType, fromNodeKey, toNodeKey, graphPerspective, description, null);
  }

  public void validate(boolean stored) {
    if (!java.util.Set.of("REMOVE_EDGE", "RENAME_NODE", "MOVE_NODE").contains(type == null ? "" : type)
      || !ArchitectureApi.LANGUAGES.contains(ecosystem == null ? "" : ecosystem)
      || !java.util.Set.of("file_graph", "namespace_graph").contains(graphType == null ? "" : graphType)) {
      throw new IllegalArgumentException("A directive requires a supported type, ecosystem and graphType.");
    }
    required(fromNodeKey, "fromNodeKey", 1000);
    required(toNodeKey, "toNodeKey", 1000);
    optional(graphPerspective, "graphPerspective", 255);
    optional(description, "description", 10000);
    optional(origin, "origin", 255);
    if (stored) {
      required(projectId, "projectId", 1000);
      if (id == null || !java.util.UUID.fromString(id).toString().equalsIgnoreCase(id)) {
        throw new IllegalArgumentException("The public directive response has an invalid id.");
      }
    }
  }

  private static void required(@Nullable String value, String field, int limit) {
    if (value == null || value.isBlank() || value.length() > limit) {
      throw new IllegalArgumentException("Directive " + field + " must be a nonempty string of at most " + limit + " characters.");
    }
  }

  private static void optional(@Nullable String value, String field, int limit) {
    if (value != null && value.length() > limit) {
      throw new IllegalArgumentException("Directive " + field + " exceeds " + limit + " characters.");
    }
  }
}
