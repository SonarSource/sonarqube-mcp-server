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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

import static org.sonarsource.sonarqube.mcp.client.TransportMode.HTTP;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.STDIO;

/**
 * Categories of tools that can be selectively enabled or disabled.
 * A tool may belong to one or more categories.
 */
public enum ToolCategory {
  ANALYSIS("analysis", STDIO, HTTP),
  IDE("ide", STDIO, HTTP),
  COVERAGE("coverage", STDIO, HTTP),
  ISSUES("issues", STDIO, HTTP),
  PROJECTS("projects", STDIO, HTTP),
  QUALITY_GATES("quality-gates", STDIO, HTTP),
  RULES("rules", STDIO, HTTP),
  SOURCES("sources"),
  DUPLICATIONS("duplications", STDIO, HTTP),
  MEASURES("measures", STDIO, HTTP),
  LANGUAGES("languages"),
  PORTFOLIOS("portfolios"),
  SYSTEM("system"),
  WEBHOOKS("webhooks"),
  DEPENDENCY_RISKS("dependency-risks", STDIO, HTTP),
  SECURITY_HOTSPOTS("security-hotspots", STDIO, HTTP),
  CAG("cag", STDIO, HTTP),
  AGENTIC_READINESS("agentic-readiness", STDIO),
  VORTEX("vortex"),
  ARCHITECTURE("architecture");

  private final String key;
  private final Set<TransportMode> defaultTransports;

  ToolCategory(String key, TransportMode... defaultTransports) {
    this.key = key;
    this.defaultTransports = Set.of(defaultTransports);
  }

  public String getKey() {
    return key;
  }

  @Nullable
  public static ToolCategory fromKey(@Nullable String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    var normalizedKey = key.trim().toLowerCase(Locale.getDefault());
    for (var category : values()) {
      if (category.key.equals(normalizedKey)) {
        return category;
      }
    }
    return null;
  }

  /**
   * Parses a comma-separated list of category keys into a Set of ToolCategory.
   * Invalid keys are silently ignored.
   */
  public static Set<ToolCategory> parseCategories(@Nullable String categoriesStr) {
    return parseCategories(categoriesStr, STDIO);
  }

  public static Set<ToolCategory> parseCategories(@Nullable String categoriesStr, TransportMode transport) {
    if (categoriesStr == null || categoriesStr.isBlank()) {
      return defaultEnabled(transport);
    }
    return Arrays.stream(categoriesStr.split(","))
      .map(String::trim)
      .map(ToolCategory::fromKey)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  /**
   * Returns the default enabled categories when SONARQUBE_TOOLSETS is not provided.
   */
  public static Set<ToolCategory> defaultEnabled() {
    return defaultEnabled(STDIO);
  }

  public static Set<ToolCategory> defaultEnabled(TransportMode transport) {
    return Arrays.stream(values())
      .filter(category -> category.defaultTransports.contains(transport))
      .collect(Collectors.toUnmodifiableSet());
  }

  public static Set<ToolCategory> all() {
    return Set.of(values());
  }
}

