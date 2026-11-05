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
package org.sonarsource.sonarqube.mcp.serverapi.settings;

import com.google.gson.Gson;
import java.util.Optional;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.settings.response.SettingValuesResponse;

public class SettingsApi {

  public static final String SETTINGS_VALUES_PATH = "/api/settings/values";

  private final ServerApiHelper helper;

  public SettingsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Optional<String> getValue(String componentKey, String key) {
    var url = new UrlBuilder(SETTINGS_VALUES_PATH)
      .addParam("keys", key)
      .addParam("component", componentKey)
      .build();

    try (var response = helper.get(url)) {
      var values = new Gson().fromJson(response.bodyAsString(), SettingValuesResponse.class);
      if (values == null || values.settings() == null) {
        return Optional.empty();
      }
      return values.settings().stream()
        .filter(setting -> setting != null && key.equals(setting.key()))
        .findFirst()
        .map(SettingValuesResponse.Setting::value);
    }
  }

}
