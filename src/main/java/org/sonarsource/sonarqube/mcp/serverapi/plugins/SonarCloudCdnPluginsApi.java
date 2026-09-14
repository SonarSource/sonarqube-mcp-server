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

import java.net.URI;
import java.net.URISyntaxException;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

public class SonarCloudCdnPluginsApi {

  public static final String PLUGIN_DOWNLOAD_PATH = "/plugins/%s/versions/%s.jar";

  private final String baseUrl;
  private final HttpClient httpClient;

  public SonarCloudCdnPluginsApi(String baseUrl, HttpClient httpClient) {
    this.baseUrl = baseUrl;
    this.httpClient = httpClient;
  }

  public HttpClient.Response downloadPlugin(String pluginKey, String md5) {
    return httpClient.getAsyncAnonymous(buildDownloadUrl(baseUrl, pluginKey, md5)).join();
  }

  static String buildDownloadUrl(String baseUrl, String pluginKey, String md5) {
    var baseUri = URI.create(baseUrl);
    var host = baseUri.getHost();
    if (host == null) {
      throw new IllegalArgumentException("SonarQube Cloud base URL must contain a host");
    }
    try {
      return new URI(baseUri.getScheme(), null, "scanner." + host, baseUri.getPort(),
        PLUGIN_DOWNLOAD_PATH.formatted(pluginKey, md5), null, null).toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to build SonarQube Cloud CDN plugin URL", e);
    }
  }

}
