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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListQualityGatesToolResponse(
  @JsonPropertyDescription("List of quality gates") List<QualityGate> qualityGates
) {
  
  public record QualityGate(
    @JsonPropertyDescription("Quality gate ID") @Nullable Long id,
    @JsonPropertyDescription("Quality gate name") String name,
    @JsonPropertyDescription("Whether this is the default quality gate") boolean isDefault,
    @JsonPropertyDescription("Whether this is a built-in quality gate") boolean isBuiltIn,
    @JsonPropertyDescription("List of conditions") @Nullable List<Condition> conditions,
    @JsonPropertyDescription("Clean as You Code status") @Nullable String caycStatus,
    @JsonPropertyDescription("Whether it has standard conditions") @Nullable Boolean hasStandardConditions,
    @JsonPropertyDescription("Whether it has MQR conditions") @Nullable Boolean hasMQRConditions,
    @JsonPropertyDescription("Whether AI code is supported") @Nullable Boolean isAiCodeSupported
  ) {}
  
  public record Condition(
    @JsonPropertyDescription("Metric key") String metric,
    @JsonPropertyDescription("Comparison operator") String op,
    @JsonPropertyDescription("Error threshold") int error
  ) {}
}

