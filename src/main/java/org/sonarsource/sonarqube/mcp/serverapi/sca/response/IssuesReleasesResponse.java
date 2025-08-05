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
package org.sonarsource.sonarqube.mcp.serverapi.sca.response;

import java.util.List;

public record IssuesReleasesResponse(List<DependencyRisk> dependencyRisks) {

  public record DependencyRisk(
    String key,
    String component,
    String severity,
    String status,
    String message,
    String rule,
    String packageName,
    String packageVersion,
    String riskType,
    List<String> cwe,
    List<String> cve,
    String creationDate,
    String updateDate
  ) {}
}