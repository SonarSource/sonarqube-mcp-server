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
package org.sonarsource.sonarqube.mcp.tools.hotspots;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

public record ShowSecurityHotspotToolResponse(
  @JsonPropertyDescription( "Unique Security Hotspot identifier")
  String key,
  @JsonPropertyDescription( "Component (file) where the Security Hotspot is located")
  String component,
  @JsonPropertyDescription( "Project key where the Security Hotspot was found")
  String project,
  @JsonPropertyDescription( "Security category (e.g., sql-injection, xss, weak-cryptography)")
  String securityCategory,
  @JsonPropertyDescription( "Vulnerability probability (HIGH, MEDIUM, LOW)")
  String vulnerabilityProbability,
  @JsonPropertyDescription( "Review status (TO_REVIEW, REVIEWED)")
  String status,
  @Nullable
  @JsonPropertyDescription( "Resolution when status is REVIEWED (FIXED, SAFE, ACKNOWLEDGED)")
  String resolution,
  @Nullable
  @JsonPropertyDescription( "Line number where the Security Hotspot is located")
  Integer line,
  @JsonPropertyDescription( "Security Hotspot description message")
  String message,
  @Nullable
  @JsonPropertyDescription( "User assigned to review the Security Hotspot")
  String assignee,
  @JsonPropertyDescription( "Author who introduced the Security Hotspot")
  String author,
  @JsonPropertyDescription( "Date when the Security Hotspot was created")
  String creationDate,
  @JsonPropertyDescription( "Date when the Security Hotspot was last updated")
  String updateDate,
  @Nullable
  @JsonPropertyDescription( "Location of the Security Hotspot in the source file")
  TextRange textRange,
  @JsonPropertyDescription( "Code flows showing the path of the security-sensitive code")
  List<Flow> flows,
  @JsonPropertyDescription( "Comments on the Security Hotspot")
  List<Comment> comments,
  @JsonPropertyDescription( "Rule that triggered the Security Hotspot")
  Rule rule,
  @JsonPropertyDescription( "Whether the current user can change the Security Hotspot status")
  String canChangeStatus
) {

  public record TextRange(
    @JsonPropertyDescription( "Starting line number")
    Integer startLine,
    @JsonPropertyDescription( "Ending line number")
    Integer endLine,
    @JsonPropertyDescription( "Starting offset in the line")
    Integer startOffset,
    @JsonPropertyDescription( "Ending offset in the line")
    Integer endOffset
  ) {}

  public record Flow(
    @JsonPropertyDescription( "Locations in the flow")
    List<Location> locations
  ) {}

  public record Location(
    @JsonPropertyDescription( "Component where the location is")
    String component,
    @JsonPropertyDescription( "Text range of the location")
    TextRange textRange,
    @JsonPropertyDescription( "Message describing the location")
    String msg
  ) {}

  public record Comment(
    @JsonPropertyDescription( "Comment identifier")
    String key,
    @JsonPropertyDescription( "Login of the user who wrote the comment")
    String login,
    @JsonPropertyDescription( "HTML-formatted comment text")
    String htmlText,
    @JsonPropertyDescription( "Markdown-formatted comment text")
    String markdown,
    @JsonPropertyDescription( "Whether the comment can be updated by the current user")
    boolean updatable,
    @JsonPropertyDescription( "Date when the comment was created")
    String createdAt
  ) {}

  public record Rule(
    @JsonPropertyDescription( "Rule key")
    String key,
    @JsonPropertyDescription( "Rule name")
    String name,
    @JsonPropertyDescription( "Security category")
    String securityCategory,
    @JsonPropertyDescription( "Vulnerability probability")
    String vulnerabilityProbability,
    @Nullable
    @JsonPropertyDescription( "Description of the security risk")
    String riskDescription,
    @Nullable
    @JsonPropertyDescription( "Description of potential vulnerabilities")
    String vulnerabilityDescription,
    @Nullable
    @JsonPropertyDescription( "Recommendations for fixing the issue")
    String fixRecommendations
  ) {}

}
