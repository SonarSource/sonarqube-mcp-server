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

public record ComponentTreeResponse(
  Paging paging,
  Component baseComponent,
  List<Component> components,
  List<Metric> metrics
) {

  public record Paging(int pageIndex, int pageSize, int total) {
  }

  public record Component(String key, String name, String qualifier, String path, List<Measure> measures) {
  }

  public record Measure(String metric, String value, boolean bestValue) {
  }

  public record Metric(String key, String name, String description, String domain, String type,
                       boolean higherValuesAreBetter, boolean qualitative, boolean hidden, boolean custom) {
  }

}
