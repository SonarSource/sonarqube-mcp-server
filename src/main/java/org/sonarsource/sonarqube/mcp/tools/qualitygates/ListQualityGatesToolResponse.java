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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListQualityGatesToolResponse(
  @Description("List of quality gates") List<QualityGate> qualityGates
) {
  
  public record QualityGate(
    @Description("Quality gate ID") @Nullable Long id,
    @Description("Quality gate name") String name,
    @Description("Whether this is the default quality gate") boolean isDefault,
    @Description("Whether this is a built-in quality gate") boolean isBuiltIn,
    @Description("List of conditions") @Nullable List<Condition> conditions,
    @Description("Clean as You Code status") @Nullable String caycStatus,
    @Description("Whether it has standard conditions") @Nullable Boolean hasStandardConditions,
    @Description("Whether it has MQR conditions") @Nullable Boolean hasMQRConditions,
    @Description("Whether AI code is supported") @Nullable Boolean isAiCodeSupported
  ) {}
  
  public record Condition(
    @Description("Metric key") String metric,
    @Description("Comparison operator") String op,
    @Description("Error threshold") int error
  ) {}
}

