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














  private void validateEnums() {
    if (!java.util.Set.of("REMOVE_EDGE", "RENAME_NODE", "MOVE_NODE").contains(type == null ? "" : type)
      || !ArchitectureApi.LANGUAGES.contains(ecosystem == null ? "" : ecosystem)
      || !java.util.Set.of("file_graph", "namespace_graph").contains(graphType == null ? "" : graphType)) {
      throw new IllegalArgumentException("A directive requires a supported type, ecosystem and graphType.");
    }
  }

  public void validateStored() {
    validateEnums();
    if (projectId == null || projectId.isBlank() || fromNodeKey == null || toNodeKey == null) {
      throw new IllegalArgumentException("The public directive response is incomplete.");
    }
    if (id == null || !java.util.UUID.fromString(id).toString().equalsIgnoreCase(id)) {
      throw new IllegalArgumentException("The public directive response has an invalid id.");
    }
  }




}
