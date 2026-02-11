/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.serverapi.hotspots.response;

import jakarta.annotation.Nullable;
import java.util.List;

public record SearchResponse(Paging paging, List<Hotspot> hotspots, List<Component> components) {

  public record Paging(Integer pageIndex, Integer pageSize, Integer total) {
  }

  public record Hotspot(String key, String component, String project, String securityCategory, String vulnerabilityProbability,
                        String status, @Nullable String resolution, @Nullable Integer line, String message, @Nullable String assignee, String author,
                        String creationDate, String updateDate, @Nullable TextRange textRange, @Nullable String ruleKey, @Nullable List<Flow> flows) {
  }

  public record Flow(List<Location> locations) {
  }

  public record Location(String component, @Nullable TextRange textRange, String msg) {
  }

  public record TextRange(Integer startLine, Integer endLine, Integer startOffset, Integer endOffset) {
  }

  public record Component(@Nullable String organization, String key, String qualifier, String name, @Nullable String longName, @Nullable String path) {
  }

}
