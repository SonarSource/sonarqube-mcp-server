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

public record ShowSecurityHotspotToolResponse(
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
  @Nullable @JsonPropertyDescription("Author") String author,
  @JsonPropertyDescription("Created at") String creationDate,
  @JsonPropertyDescription("Updated at") String updateDate,
  @Nullable @JsonPropertyDescription("Source range") TextRange textRange,
  @JsonPropertyDescription("Code flows showing the path of the security-sensitive code") List<Flow> flows,
  @JsonPropertyDescription("Comments") List<Comment> comments,
  @JsonPropertyDescription("Rule that triggered the hotspot") Rule rule,
  @JsonPropertyDescription("Current user can change status") boolean canChangeStatus
) {

  public record TextRange(
    @JsonPropertyDescription("Starting line number") Integer startLine,
    @JsonPropertyDescription("Ending line number") Integer endLine,
    @JsonPropertyDescription("Starting offset in the line") Integer startOffset,
    @JsonPropertyDescription("Ending offset in the line") Integer endOffset
  ) {}

  public record Flow(
    @JsonPropertyDescription("Locations in the flow") List<Location> locations
  ) {}

  public record Location(
    @JsonPropertyDescription("Component where the location is") String component,
    @JsonPropertyDescription("Text range of the location") TextRange textRange,
    @JsonPropertyDescription("Message describing the location") String msg
  ) {}

  public record Comment(
    @JsonPropertyDescription("Comment identifier") String key,
    @JsonPropertyDescription("Login of the user who wrote the comment") String login,
    @JsonPropertyDescription("HTML-formatted comment text") String htmlText,
    @JsonPropertyDescription("Markdown-formatted comment text") String markdown,
    @JsonPropertyDescription("Whether the comment can be updated by the current user") boolean updatable,
    @JsonPropertyDescription("Date when the comment was created") String createdAt
  ) {}

  public record Rule(
    @JsonPropertyDescription("Rule key") String key,
    @JsonPropertyDescription("Rule name") String name,
    @JsonPropertyDescription("Security category") String securityCategory,
    @JsonPropertyDescription("Vulnerability probability") String vulnerabilityProbability,
    @Nullable @JsonPropertyDescription("Description of the security risk") String riskDescription,
    @Nullable @JsonPropertyDescription("Description of potential vulnerabilities") String vulnerabilityDescription,
    @Nullable @JsonPropertyDescription("Recommendations for fixing the issue") String fixRecommendations
  ) {}

}
