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
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShowRuleToolResponse(
  @Description("Unique rule key") String key,
  @Description("Rule display name") String name,
  @Description("Rule severity level") String severity,
  @Description("Rule type (BUG, VULNERABILITY, CODE_SMELL, etc.)") String type,
  @Description("Language key the rule applies to") String lang,
  @Description("Human-readable language name") String langName,
  @Description("HTML description of the rule") @Nullable String htmlDesc,
  @Description("Software quality impacts of this rule") @Nullable List<Impact> impacts,
  @Description("Detailed description sections") @Nullable List<DescriptionSection> descriptionSections
) {
  
  public record Impact(
    @Description("Software quality dimension (MAINTAINABILITY, RELIABILITY, SECURITY)") String softwareQuality,
    @Description("Impact severity level") String severity
  ) {}
  
  public record DescriptionSection(
    @Description("Section content in HTML format") String content
  ) {}
}


