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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetComponentMeasuresToolResponse(
  Component component,
  List<Measure> measures,
  @Nullable List<Metric> metrics
) {
  
  public record Component(
    String key,
    String name,
    String qualifier,
    @Nullable String description,
    @Nullable String language,
    @Nullable String path
  ) {}
  
  public record Measure(
    String metric,
    @Nullable String value,
    @Nullable Integer period,
    @Nullable Boolean bestValue
  ) {}
  
  public record Metric(
    String key,
    String name,
    String description,
    String domain,
    String type,
    boolean hidden,
    boolean custom
  ) {}
}

