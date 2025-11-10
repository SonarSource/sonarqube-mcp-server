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
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectStatusToolResponse(
  @JsonPropertyDescription("Overall quality gate status (OK, WARN, ERROR, etc.)") String status,
  @JsonPropertyDescription("List of quality gate conditions") List<Condition> conditions,
  @JsonPropertyDescription("Whether the quality gate is ignored") @Nullable Boolean ignoredConditions
) {
  
  public record Condition(
    @JsonPropertyDescription("Metric key") String metricKey,
    @JsonPropertyDescription("Condition status (OK, ERROR, etc.)") String status,
    @JsonPropertyDescription("Error threshold value") @Nullable String errorThreshold,
    @JsonPropertyDescription("Metric actual value") @Nullable String actualValue
  ) {}
}

