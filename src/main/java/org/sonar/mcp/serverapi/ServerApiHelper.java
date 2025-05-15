/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.serverapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

public class ServerApiHelper {

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  public HttpClient.Response get(String path) {
    var response = rawGet(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute GET and don't check response
   */
  public HttpClient.Response rawGet(String relativePath) {
    return rawGetUrl(buildEndpointUrl(relativePath));
  }

  private HttpClient.Response rawGetUrl(String url) {
    return client.getAsync(url).join();
  }

  private String buildEndpointUrl(String relativePath) {
    return concat(endpointParams.getBaseUrl(), relativePath);
  }

  public static String concat(String baseUrl, String relativePath) {
    return StringUtils.appendIfMissing(baseUrl, "/") +
      (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
  }

  public static RuntimeException handleError(HttpClient.Response toBeClosed) {
    try (var failedResponse = toBeClosed) {
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new UnauthorizedException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        var jsonError = tryParseAsJsonError(failedResponse);
        // Details are in response content
        return new ForbiddenException(jsonError != null ? jsonError : "Forbidden");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return new NotFoundException(formatHttpFailedResponse(failedResponse, null));
      }
      if (failedResponse.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
        return new ServerErrorException(formatHttpFailedResponse(failedResponse, null));
      }

      var errorMsg = tryParseAsJsonError(failedResponse);

      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(HttpClient.Response failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.url() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  @CheckForNull
  private static String tryParseAsJsonError(HttpClient.Response response) {
    var content = response.bodyAsString();
    if (StringUtils.isBlank(content)) {
      return null;
    }
    var obj = JsonParser.parseString(content).getAsJsonObject();
    var errors = obj.getAsJsonArray("errors");
    if (errors == null) {
      return null;
    }
    List<String> errorMessages = new ArrayList<>();
    for (JsonElement e : errors) {
      errorMessages.add(e.getAsJsonObject().get("msg").getAsString());
    }
    return String.join(", ", errorMessages);
  }

}
