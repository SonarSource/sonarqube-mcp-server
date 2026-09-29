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
package org.sonarsource.sonarqube.mcp.tools.hotspots;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

public record SearchSecurityHotspotsToolResponse(
  @JsonPropertyDescription("Matching hotspots") List<Hotspot> hotspots,
  @JsonPropertyDescription("Pagination") Paging paging) {

  public record Hotspot(
    @JsonPropertyDescription("Hotspot key") String key,
    @JsonPropertyDescription("File key") String component,
    @JsonPropertyDescription("Project key") String project,
    @JsonPropertyDescription("Security category") String securityCategory,
    @JsonPropertyDescription("HIGH, MEDIUM, or LOW") String vulnerabilityProbability,
    @JsonPropertyDescription("TO_REVIEW or REVIEWED") String status,
    @Nullable @JsonPropertyDescription("Present when REVIEWED") String resolution,
    @Nullable @JsonPropertyDescription("Line") Integer line,
    @JsonPropertyDescription("Message") String message,
    @Nullable @JsonPropertyDescription("Assignee") String assignee,
    @JsonPropertyDescription("Author") String author,
    @JsonPropertyDescription("Created at") String creationDate,
    @JsonPropertyDescription("Updated at") String updateDate,
    @Nullable @JsonPropertyDescription("Source range") TextRange textRange,
    @Nullable @JsonPropertyDescription("Rule key") String ruleKey
  ) {}

  public record TextRange(
    @JsonPropertyDescription("Starting line number") Integer startLine,
    @JsonPropertyDescription("Ending line number") Integer endLine,
    @JsonPropertyDescription("Starting offset in the line") Integer startOffset,
    @JsonPropertyDescription("Ending offset in the line") Integer endOffset
  ) {}

  public record Paging(
    @JsonPropertyDescription("Page index") Integer pageIndex,
    @JsonPropertyDescription("Page size") Integer pageSize,
    @JsonPropertyDescription("Total items") Integer total
  ) {}

}
