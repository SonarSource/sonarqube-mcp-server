/*
 * Sonar MCP Server
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
package org.sonar.mcp.serverapi;

import jakarta.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UrlBuilder {
  private final String path;
  private final List<QueryParam> parameters = new ArrayList<>();

  public UrlBuilder(String path) {
    this.path = path;
  }

  public UrlBuilder addParam(String name, @Nullable String value) {
    if (value != null) {
      parameters.add(new QueryParam(name, List.of(value)));
    }
    return this;
  }

  public UrlBuilder addParam(String name, @Nullable List<String> values) {
    if (values != null && !values.isEmpty()) {
      parameters.add(new QueryParam(name, values));
    }
    return this;
  }

  public String build() {
    var url = new StringBuilder(path);
    var leadingCharacter = '?';
    for (var parameter : parameters) {
      url.append(leadingCharacter)
        .append(parameter.name)
        .append("=")
        .append(String.join(",", parameter.values.stream().map(UrlBuilder::urlEncode).toList()));
      leadingCharacter = '&';
    }
    return url.toString();
  }

  private static String urlEncode(String string) {
    return URLEncoder.encode(string, StandardCharsets.UTF_8);
  }

  private record QueryParam(String name, List<String> values) {
  }
}
