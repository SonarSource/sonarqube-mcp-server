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
package org.sonarsource.sonarqube.mcp.tools.metrics;

import java.util.List;
import jakarta.annotation.Nullable;

public record SearchMetricsToolResponse(
  List<Metric> metrics,
  int total,
  int page,
  int pageSize
) {
  
  public record Metric(
    String id,
    String key,
    String name,
    @Nullable String description,
    @Nullable String domain,
    String type,
    boolean hidden,
    boolean custom
  ) {}
}


