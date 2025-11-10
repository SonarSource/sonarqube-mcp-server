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
package org.sonarsource.sonarqube.mcp.serverapi.rules.response;

import java.util.List;

public record ShowResponse(Rule rule, List<String> actives) {

  public record Rule(
    String key,
    String repo,
    String name,
    String createdAt,
    String htmlDesc,
    String mdDesc,
    String severity,
    String status,
    boolean isTemplate,
    List<String> tags,
    List<String> sysTags,
    String lang,
    String langName,
    List<Param> params,
    String defaultDebtRemFnType,
    String defaultDebtRemFnCoeff,
    String defaultDebtRemFnOffset,
    String effortToFixDescription,
    boolean debtOverloaded,
    String debtRemFnType,
    String debtRemFnCoeff,
    String debtRemFnOffset,
    String type,
    String defaultRemFnType,
    String defaultRemFnGapMultiplier,
    String defaultRemFnBaseEffort,
    String remFnType,
    String remFnGapMultiplier,
    String remFnBaseEffort,
    boolean remFnOverloaded,
    String gapDescription,
    String scope,
    boolean isExternal,
    List<DescriptionSection> descriptionSections,
    List<String> educationPrinciples,
    String cleanCodeAttribute,
    String cleanCodeAttributeCategory,
    List<Impact> impacts
  ) {
  }

  public record Impact(String softwareQuality, String severity) {
  }

  public record Param(String key, String htmlDesc, String defaultValue, String type) {
  }

  public record DescriptionSection(String key, String content) {
  }

}
