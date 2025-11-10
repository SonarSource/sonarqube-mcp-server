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
package org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response;

import java.util.List;
import javax.annotation.Nullable;

public record ListResponse(List<QualityGate> qualitygates, long default_id, Actions actions) {

  public record QualityGate(
    String name,
    boolean isDefault,
    boolean isBuiltIn,
    Actions actions,
    // SQ:S only fields below
    @Nullable
    Long id,
    @Nullable
    List<Condition> conditions,
    // SQ:C only fields below
    @Nullable
    String caycStatus,
    @Nullable
    Boolean hasStandardConditions,
    @Nullable
    Boolean hasMQRConditions,
    @Nullable
    Boolean isAiCodeSupported
  ) {
  }

  public record Actions(boolean rename, boolean setAsDefault, boolean copy, boolean associateProjects, boolean delete, boolean manageConditions) {
  }

  public record Condition(long id, String metric, String op, int error) {
  }

}
