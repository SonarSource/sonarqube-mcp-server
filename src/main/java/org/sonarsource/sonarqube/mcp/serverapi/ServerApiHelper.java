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
package org.sonarsource.sonarqube.mcp.serverapi;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

public class ServerApiHelper {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  @Nullable
  public String getOrganization() {
    return endpointParams.organization();
  }

  public ServerApiHelper withOrganization(String organization) {
    return new ServerApiHelper(new EndpointParams(endpointParams.baseUrl(), organization, endpointParams.apiBaseUrl(), endpointParams.isSonarQubeCloud()), client);
  }

  public boolean isSonarQubeCloud() {
    return endpointParams.isSonarQubeCloud();
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

  public HttpClient.Response patch(String path, String contentType, String body) {
    var response = awaitResponse(client.patchAsync(buildEndpointUrl(path), contentType, body));
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response post(String path, String contentType, String body, Duration timeout) {
    var response = awaitResponse(client.postAsync(buildEndpointUrl(path), contentType, body, timeout));
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute GET and don't check response
   */
  public HttpClient.Response rawGet(String relativePath) {
    return awaitResponse(client.getAsync(buildEndpointUrl(relativePath)));
  }

  public HttpClient.Response rawGet(String relativePath, Duration timeout) {
    return awaitResponse(client.getAsync(buildEndpointUrl(relativePath), timeout));
  }

  public HttpClient.Response rawGetAnonymous(String relativePath) {
    return awaitResponse(client.getAsyncAnonymous(buildEndpointUrl(relativePath)));
  }

  public HttpClient.Response rawGetAnonymous(String relativePath, Duration timeout) {
    return awaitResponse(client.getAsyncAnonymous(buildEndpointUrl(relativePath), timeout));
  }

  private HttpClient.Response rawPost(String url, String contentType, String body) {
    return awaitResponse(client.postAsync(url, contentType, body));
  }

  /**
   * Execute GET using the API subdomain (api.sonarcloud.io / api.sonarqube.us)
   */
  public HttpClient.Response getApiSubdomain(String path) {
    var response = rawGetApiSubdomain(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute raw GET using the API subdomain (api.sonarcloud.io / api.sonarqube.us)
   */
  public HttpClient.Response rawGetApiSubdomain(String relativePath) {
    return awaitResponse(client.getAsync(buildApiSubdomainUrl(relativePath)));
  }

  /**
   * Execute POST using the API subdomain (api.sonarcloud.io / api.sonarqube.us)
   */
  public HttpClient.Response postApiSubdomain(String path, String contentType, String body) {
    var response = rawPostApiSubdomain(path, contentType, body);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute raw POST using the API subdomain (api.sonarcloud.io / api.sonarqube.us)
   */
  public HttpClient.Response rawPostApiSubdomain(String relativePath, String contentType, String body) {
    return awaitResponse(client.postAsync(buildApiSubdomainUrl(relativePath), contentType, body));
  }

  /**
   * Execute PATCH using the API subdomain (api.sonarcloud.io / api.sonarqube.us).
   */
  public HttpClient.Response patchApiSubdomain(String path, String contentType, String body) {
    var response = awaitResponse(client.patchAsync(buildApiSubdomainUrl(path), contentType, body));
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response delete(String path) {
    var response = client.deleteAsync(buildEndpointUrl(path)).join();
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response deleteApiSubdomain(String path) {
    var response = awaitResponse(client.deleteAsync(buildApiSubdomainUrl(path)));
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Wait for a response, cancelling the request if the caller is interrupted.
   */
  public static HttpClient.Response awaitResponse(CompletableFuture<HttpClient.Response> request) {
    try {
      return request.get();
    } catch (InterruptedException e) {
      request.cancel(true);
      Thread.currentThread().interrupt();
      throw new CancellationException("HTTP request interrupted");
    } catch (ExecutionException e) {
      throw new CompletionException(e.getCause());
    }
  }

  private String buildEndpointUrl(String relativePath) {
    return concat(endpointParams.baseUrl(), relativePath);
  }

  /**
   * Build URL using the API subdomain (api.sonarcloud.io / api.sonarqube.us).
   * When an explicit {@code apiBaseUrl} override is set on the endpoint params (via
   * {@code SONARQUBE_CLOUD_API_URL}), it is used directly and takes priority over everything else.
   * For SonarQube Server ({@code isSonarQubeCloud == false}), falls back to the base URL.
   * For SonarQube Cloud, derives the api.* subdomain automatically for known hosts
   * (sonarcloud.io, sonarqube.us), or uses the base URL as-is for unknown hosts.
   */
  @VisibleForTesting
  String buildApiSubdomainUrl(String relativePath) {
    if (endpointParams.apiBaseUrl() != null) {
      return concat(endpointParams.apiBaseUrl(), relativePath);
    }

    if (!endpointParams.isSonarQubeCloud()) {
      // For SonarQube Server, fall back to regular endpoint
      return buildEndpointUrl(relativePath);
    }

    var baseUrl = endpointParams.baseUrl();
    try {
      var uri = URI.create(baseUrl);
      var host = uri.getHost();
      if ("sonarcloud.io".equals(host)) {
        baseUrl = uri.toString().replace("://" + host, "://api.sonarcloud.io");
      } else if ("sonarqube.us".equals(host)) {
        baseUrl = uri.toString().replace("://" + host, "://api.sonarqube.us");
      }
    } catch (IllegalArgumentException e) {
      // Malformed base URL – fall through and use it as-is
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
      if (failedResponse.code() >= HttpURLConnection.HTTP_BAD_REQUEST) {
        return new ClientErrorException(failedResponse.code(), formatHttpFailedResponse(failedResponse, errorMsg));
      }
      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(HttpClient.Response failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.url() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  @Nullable
  private static String tryParseAsJsonError(String content) {
    if (StringUtils.isBlank(content)) {
      return null;
    }
    try {
      var parsed = JsonParser.parseString(content);
      if (!parsed.isJsonObject()) {
        return null;
      }
      var obj = parsed.getAsJsonObject();
      var errorsMessage = extractErrorMessages(obj.get("errors"));
      if (errorsMessage != null) {
        return errorsMessage;
      }
      JsonElement messageElement = obj.get("message");
      if (messageElement != null && messageElement.isJsonPrimitive() && messageElement.getAsJsonPrimitive().isString()) {
        return messageElement.getAsString();
      }
    } catch (JsonParseException e) {
      return null;
    }
    return null;
  }

  @Nullable
  private static String extractErrorMessages(@Nullable JsonElement errors) {
    if (errors == null || !errors.isJsonArray()) {
      return null;
    }
    var messages = new ArrayList<String>();
    for (var error : errors.getAsJsonArray()) {
      if (!error.isJsonObject()) {
        return null;
      }
      var message = error.getAsJsonObject().get("msg");
      if (message == null || !message.isJsonPrimitive() || !message.getAsJsonPrimitive().isString()) {
        return null;
      }
      messages.add(message.getAsString());
    }
    return messages.isEmpty() ? null : String.join(", ", messages);
  }

}
