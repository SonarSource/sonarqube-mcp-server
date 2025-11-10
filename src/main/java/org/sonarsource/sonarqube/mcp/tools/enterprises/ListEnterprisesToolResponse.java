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
package org.sonarsource.sonarqube.mcp.tools.enterprises;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListEnterprisesToolResponse(
  @JsonPropertyDescription("List of available enterprises") List<Enterprise> enterprises
) {
  
  public record Enterprise(
    @JsonPropertyDescription("Enterprise unique identifier") String id,
    @JsonPropertyDescription("Enterprise key") String key,
    @JsonPropertyDescription("Enterprise display name") String name,
    @JsonPropertyDescription("Avatar URL") @Nullable String avatar,
    @JsonPropertyDescription("Default portfolio permission template ID") @Nullable String defaultPortfolioPermissionTemplateId
  ) {}
}


