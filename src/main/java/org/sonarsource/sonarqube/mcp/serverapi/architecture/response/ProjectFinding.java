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
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

public record ProjectFinding(String id, String projectId, String category, @Nullable String message, @Nullable List<String> components,
  @Nullable List<Relationship> relationships, @Nullable String constraint, @Nullable Map<String, Double> measures) {

  public record Relationship(String from, String to) {
  }

  public void validateStored() {
    if (id == null || id.isBlank() || projectId == null || projectId.isBlank()) {
      throw new IllegalStateException("The public finding response has an invalid id or project.");
    }
    if (!ArchitectureApi.FINDING_CATEGORIES.contains(category == null ? "" : category)) {
      throw new IllegalStateException("The public finding " + id + " has an unsupported category.");
    }
  }
}
