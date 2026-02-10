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
package org.sonarsource.sonarqube.mcp.serverapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

public class ServerApiHelper {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final int MAX_LOGGED_BODY_CHARS = 1000;

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  @CheckForNull
  public String getOrganization() {
    return endpointParams.organization();
  }

  public HttpClient.Response get(String path) {
    var response = rawGet(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response getAnonymous(String path) {
    var response = rawGetAnonymous(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response post(String path, String contentType, String body) {
    var response = rawPost(buildEndpointUrl(path), contentType, body);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute GET and don't check response
   */
  public HttpClient.Response rawGet(String relativePath) {
    return client.getAsync(buildEndpointUrl(relativePath)).join();
  }

  public HttpClient.Response rawGetAnonymous(String relativePath) {
    return client.getAsyncAnonymous(buildEndpointUrl(relativePath)).join();
  }

  private HttpClient.Response rawPost(String url, String contentType, String body) {
    return client.postAsync(url, contentType, body).join();
  }

  /**
   * Execute GET using the API subdomain (api.sonarcloud.io)
   */
  public HttpClient.Response getApiSubdomain(String path) {
    var response = rawGetApiSubdomain(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute raw GET using the API subdomain (api.sonarcloud.io)
   */
  public HttpClient.Response rawGetApiSubdomain(String relativePath) {
    return client.getAsync(buildApiSubdomainUrl(relativePath)).join();
  }

  /**
   * Execute POST using the API subdomain (api.sonarcloud.io)
   */
  public HttpClient.Response postApiSubdomain(String path, String contentType, String body) {
    var response = rawPostApiSubdomain(path, contentType, body);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute raw POST using the API subdomain (api.sonarcloud.io)
   */
  public HttpClient.Response rawPostApiSubdomain(String relativePath, String contentType, String body) {
    return client.postAsync(buildApiSubdomainUrl(relativePath), contentType, body).join();
  }

  private String buildEndpointUrl(String relativePath) {
    return concat(endpointParams.baseUrl(), relativePath);
  }

  /**
   * Build URL using the API subdomain (api.sonarcloud.io)
   */
  private String buildApiSubdomainUrl(String relativePath) {
    if (endpointParams.organization() == null) {
      // For SonarQube Server, fall back to regular endpoint
      return buildEndpointUrl(relativePath);
    }

    var baseUrl = endpointParams.baseUrl();
    // Transform sonarcloud.io to api.sonarcloud.io
    if (baseUrl.contains("sonarcloud.io")) {
      baseUrl = baseUrl.replace("://sonarcloud.io", "://api.sonarcloud.io");
    }
    return concat(baseUrl, relativePath);
  }

  public static String concat(String baseUrl, String relativePath) {
    return Strings.CS.appendIfMissing(baseUrl, "/") +
      (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
  }

  public static RuntimeException handleError(HttpClient.Response toBeClosed) {
    try (var failedResponse = toBeClosed) {
      var responseBody = failedResponse.bodyAsString();
      LOG.debug("HTTP error - URL: " + failedResponse.url() + ", status: " + failedResponse.code());
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new UnauthorizedException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        var jsonError = tryParseAsJsonError(responseBody);
        // Details are in response content
        return new ForbiddenException(jsonError != null ? jsonError : "Forbidden");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return new NotFoundException(formatHttpFailedResponse(failedResponse, null));
      }
      if (failedResponse.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
        return new ServerInternalErrorException(formatHttpFailedResponse(failedResponse, null));
      }

      var errorMsg = tryParseAsJsonError(responseBody);

      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(HttpClient.Response failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.url() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  private static String truncate(@Nullable String value) {
    if (value == null) {
      return "";
    }
    if (value.length() <= MAX_LOGGED_BODY_CHARS) {
      return value;
    }
    return value.substring(0, MAX_LOGGED_BODY_CHARS) + "... [truncated, total length: " + value.length() + "]";
  }

  @CheckForNull
  private static String tryParseAsJsonError(String content) {
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
