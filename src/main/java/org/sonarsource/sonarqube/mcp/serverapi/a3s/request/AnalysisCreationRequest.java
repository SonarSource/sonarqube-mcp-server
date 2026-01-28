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
package org.sonarsource.sonarqube.mcp.serverapi.a3s.request;

import javax.annotation.Nullable;

/**
 * Request body for POST /a3s-analysishub/analyses
 */
public record AnalysisCreationRequest(
  @Nullable String organizationId,
  @Nullable String organizationKey,
  @Nullable String projectId,
  @Nullable String projectKey,
  @Nullable String branchId,
  @Nullable String branchName,
  String filePath,
  String fileContent,
  @Nullable String patchContent,
  @Nullable String fileScope
) {
}
