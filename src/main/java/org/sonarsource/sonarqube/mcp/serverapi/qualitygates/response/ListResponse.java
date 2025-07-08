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
package org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response;

import java.util.List;

public record ListResponse(List<QualityGate> qualitygates, long default_id, Actions actions) {

  public record QualityGate(
    // SQ:S only
    Long id,
    String name,
    Boolean isDefault,
    Boolean isBuiltIn,
    Actions actions,
    // SQ:S only
    List<Condition> conditions,
    // SQ:C only fields below
    String caycStatus,
    Boolean hasStandardConditions,
    Boolean hasMQRConditions,
    Boolean isAiCodeSupported
  ) {
  }

  public record Actions(boolean rename, boolean setAsDefault, boolean copy, boolean associateProjects, boolean delete, boolean manageConditions) {
  }

  public record Condition(long id, String metric, String op, int error) {
  }

}
