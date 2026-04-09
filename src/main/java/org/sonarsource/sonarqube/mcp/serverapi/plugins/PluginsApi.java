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
package org.sonarsource.sonarqube.mcp.serverapi.plugins;

import com.google.gson.Gson;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.response.InstalledPluginsResponse;

public class PluginsApi {

  public static final String INSTALLED_PLUGINS_PATH = "/api/plugins/installed";
  public static final String DOWNLOAD_PLUGINS_PATH = "/api/plugins/download";

  private final ServerApiHelper helper;
  private final boolean isSonarQubeCloud;

  public PluginsApi(ServerApiHelper helper, boolean isSonarQubeCloud) {
    this.helper = helper;
    this.isSonarQubeCloud = isSonarQubeCloud;
  }

  public InstalledPluginsResponse getInstalled() {
    // On SonarQube Cloud, plugin endpoints don't require authentication
    var response = isSonarQubeCloud ? helper.getAnonymous(INSTALLED_PLUGINS_PATH) : helper.get(INSTALLED_PLUGINS_PATH);
    try (response) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, InstalledPluginsResponse.class);
    }
  }

  public HttpClient.Response downloadPlugin(String pluginKey) {
    var downloadPath = DOWNLOAD_PLUGINS_PATH + "?plugin=" + pluginKey;
    // On SonarQube Cloud, plugin endpoints don't require authentication
    return isSonarQubeCloud ? helper.rawGetAnonymous(downloadPath) : helper.rawGet(downloadPath);
  }

}
