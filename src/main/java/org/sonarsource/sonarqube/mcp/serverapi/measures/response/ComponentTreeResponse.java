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
package org.sonarsource.sonarqube.mcp.serverapi.measures.response;

import java.util.List;
import javax.annotation.Nullable;

public record ComponentTreeResponse(
  @Nullable BaseComponent baseComponent,
  List<Component> components,
  Paging paging
) {
  
  public record BaseComponent(
    String key,
    String name,
    String qualifier,
    @Nullable String description
  ) {}
  
  public record Component(
    String key,
    String name,
    String qualifier,
    @Nullable String path,
    @Nullable String language,
    @Nullable List<Measure> measures
  ) {}
  
  public record Measure(
    String metric,
    @Nullable String value,
    @Nullable Boolean bestValue
  ) {}
  
  public record Paging(
    int pageIndex,
    int pageSize,
    int total
  ) {}
}
