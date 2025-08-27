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
package org.sonarsource.sonarqube.mcp.serverapi.enterprises.response;

import java.util.List;
import javax.annotation.Nullable;

// Note: This represents a direct array response, not wrapped in an object
// The API returns an array of enterprises directly
public record ListResponse(List<Enterprise> enterprises) {

  public record Enterprise(
    String id,
    String key,
    String name,
    @Nullable String avatar,
    @Nullable String defaultPortfolioPermissionTemplateId
  ) {
  }

}
