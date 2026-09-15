/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.configuration;

import java.util.function.Function;
import jakarta.annotation.Nullable;

/**
 * Shared lookup for Streamable HTTP per-request policy headers.
 * Alias order is underscore, hyphen, then {@code X-} hyphen; the first non-blank value wins.
 * Servlet {@code getHeader} is case-insensitive, so callers can pass {@code request::getHeader}.
 */
public final class HttpPolicyHeaders {

  public static final String READ_ONLY = McpServerLaunchConfiguration.SONARQUBE_READ_ONLY;
  public static final String READ_ONLY_HYPHEN = "SONARQUBE-READ-ONLY";
  public static final String READ_ONLY_X = "X-SONARQUBE-READ-ONLY";

  public static final String TOOLSETS = McpServerLaunchConfiguration.SONARQUBE_TOOLSETS;
  public static final String TOOLSETS_HYPHEN = "SONARQUBE-TOOLSETS";
  public static final String TOOLSETS_X = "X-SONARQUBE-TOOLSETS";

  private HttpPolicyHeaders() {
    // utility class
  }

  @Nullable
  public static String readOnlyValue(Function<String, String> headerLookup) {
    return firstNonBlank(headerLookup, READ_ONLY, READ_ONLY_HYPHEN, READ_ONLY_X);
  }

  @Nullable
  public static String toolsetsValue(Function<String, String> headerLookup) {
    return firstNonBlank(headerLookup, TOOLSETS, TOOLSETS_HYPHEN, TOOLSETS_X);
  }

  /**
   * Missing or blank is valid (header omitted). Otherwise only {@code true} or {@code false}, ignoring case.
   * Do not use {@link Boolean#parseBoolean(String)} for this check.
   */
  public static boolean isValidReadOnlyValue(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    var trimmed = value.trim();
    return "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed);
  }

  /**
   * Returns {@link Boolean#TRUE}/{@link Boolean#FALSE} for true/false ignoring case.
   * Returns {@code null} when the value is missing, blank, or anything other than true/false.
   */
  @Nullable
  public static Boolean parseStrictBoolean(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    var trimmed = value.trim();
    if ("true".equalsIgnoreCase(trimmed)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(trimmed)) {
      return Boolean.FALSE;
    }
    return null;
  }

  @Nullable
  private static String firstNonBlank(Function<String, String> headerLookup, String... names) {
    for (var name : names) {
      var value = headerLookup.apply(name);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

}
