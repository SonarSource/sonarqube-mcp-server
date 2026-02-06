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
package org.sonarsource.sonarqube.mcp.serverapi.measures;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Parameters for the component tree API call.
 *
 * @param component Component key
 * @param branch Branch key
 * @param metricKeys Comma-separated list of metric keys
 * @param pullRequest Pull request id
 * @param qualifiers Comma-separated list of component qualifiers. Filter the results with the specified qualifiers. Possible values are: BRC, DIR, FIL, TRK, UTS
 * @param strategy Strategy to select children: 'children' returns direct children, 'all' returns all descendants, 'leaves' returns only leaves
 * @param sort Sort field. Can be 'metric', 'name', 'path', or 'qualifier'
 * @param metricSort Metric key to sort by when sort='metric'
 * @param asc Whether to sort in ascending order
 * @param pageIndex Page number (1-indexed)
 * @param pageSize Page size
 */
public record ComponentTreeParams(
  @Nullable String component,
  @Nullable String branch,
  @Nullable List<String> metricKeys,
  @Nullable String pullRequest,
  @Nullable String qualifiers,
  @Nullable String strategy,
  @Nullable String sort,
  @Nullable String metricSort,
  @Nullable Boolean asc,
  @Nullable Integer pageIndex,
  @Nullable Integer pageSize
) {
}
