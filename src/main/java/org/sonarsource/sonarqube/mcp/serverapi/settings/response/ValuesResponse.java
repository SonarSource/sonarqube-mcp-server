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
package org.sonarsource.sonarqube.mcp.serverapi.settings.response;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

public record ValuesResponse(
  List<Setting> settings,
  @Nullable List<String> setSecuredSettings
) {

  public record Setting(
    String key,
    @Nullable String value,
    @Nullable List<String> values,
    @Nullable List<Map<String, Object>> fieldValues,
    boolean inherited
  ) {}

  /**
   * Helper method to get a specific setting value by key
   * @param key the setting key to look for
   * @return the setting value if found, null otherwise
   */
  public String getSettingValue(String key) {
    if (settings == null) {
      return null;
    }
    
    return settings.stream()
      .filter(setting -> key.equals(setting.key()))
      .map(Setting::value)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  /**
   * Helper method to check if a boolean setting is enabled
   * @param key the setting key to check
   * @return true if the setting exists and is set to "true", false otherwise
   */
  public boolean isBooleanSettingEnabled(String key) {
    return "true".equalsIgnoreCase(getSettingValue(key));
  }

}
