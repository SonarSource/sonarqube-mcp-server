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
package org.sonarsource.sonarqube.mcp.tools.rules;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ListRuleRepositoriesToolResponse(
  @JsonPropertyDescription("List of rule repositories") List<Repository> repositories
) {
  
  public record Repository(
    @JsonPropertyDescription("Repository key identifier") String key,
    @JsonPropertyDescription("Repository display name") String name,
    @JsonPropertyDescription("Language the repository applies to") String language
  ) {}
}


