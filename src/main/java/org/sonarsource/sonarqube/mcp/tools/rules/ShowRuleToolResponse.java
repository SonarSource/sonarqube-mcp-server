/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowRuleToolResponse(
  @JsonPropertyDescription("Unique rule key") String key,
  @JsonPropertyDescription("Rule display name") String name,
  @JsonPropertyDescription("Rule severity level") String severity,
  @JsonPropertyDescription("Rule type (BUG, VULNERABILITY, CODE_SMELL, etc.)") String type,
  @JsonPropertyDescription("Language key the rule applies to") String lang,
  @JsonPropertyDescription("Human-readable language name") String langName,
  @JsonPropertyDescription("HTML description of the rule") @Nullable String htmlDesc,
  @JsonPropertyDescription("Software quality impacts of this rule") @Nullable List<Impact> impacts,
  @JsonPropertyDescription("Detailed description sections") @Nullable List<DescriptionSection> descriptionSections
) {
  
  public record Impact(
    @JsonPropertyDescription("Software quality dimension (MAINTAINABILITY, RELIABILITY, SECURITY)") String softwareQuality,
    @JsonPropertyDescription("Impact severity level") String severity
  ) {}
  
  public record DescriptionSection(
    @JsonPropertyDescription("Section content in HTML format") String content
  ) {}
}


