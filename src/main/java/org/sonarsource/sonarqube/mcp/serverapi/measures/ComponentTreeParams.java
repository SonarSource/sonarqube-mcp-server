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
