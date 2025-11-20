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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Categories of tools that can be selectively enabled or disabled.
 * Each tool belongs to exactly one category.
 */
public enum ToolCategory {
  ANALYSIS("analysis"),
  ISSUES("issues"),
  PROJECTS("projects"),
  QUALITY_GATES("quality-gates"),
  RULES("rules"),
  SOURCES("sources"),
  MEASURES("measures"),
  LANGUAGES("languages"),
  PORTFOLIOS("portfolios"),
  SYSTEM("system"),
  WEBHOOKS("webhooks"),
  DEPENDENCY_RISKS("dependency-risks");

  private final String key;

  ToolCategory(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  @CheckForNull
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
    if (categoriesStr == null || categoriesStr.isBlank()) {
      return all();
    }
    return Arrays.stream(categoriesStr.split(","))
      .map(String::trim)
      .map(ToolCategory::fromKey)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public static Set<ToolCategory> all() {
    return Set.of(values());
  }
}

