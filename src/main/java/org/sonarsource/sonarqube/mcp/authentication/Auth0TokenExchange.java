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
package org.sonarsource.sonarqube.mcp.authentication;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class Auth0TokenExchange {
  private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
  private static final String ERROR_FIELD = "error";
  private final OAuthConfiguration configuration;
  private final HttpClient client;
  private final URI endpoint;

  Auth0TokenExchange(OAuthConfiguration configuration) {
    this(configuration, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
      URI.create(configuration.issuer()).resolve("oauth/token"));
  }

  Auth0TokenExchange(OAuthConfiguration configuration, HttpClient client, URI endpoint) {
    this.configuration = configuration;
    this.client = client;
    this.endpoint = endpoint;
  }

  String exchange(String token, Set<String> scopes) {
    var fields = new LinkedHashMap<String, String>();
    fields.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
    fields.put("subject_token_type", ACCESS_TOKEN_TYPE);
    fields.put("requested_token_type", ACCESS_TOKEN_TYPE);
    fields.put("subject_token", token);
    fields.put("audience", configuration.cloudAudience());
    fields.put("scope", scopes.stream().sorted().collect(Collectors.joining(" ")));
    fields.put("client_id", configuration.clientId());
    fields.put("client_secret", configuration.clientSecret());
    var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      byte[] bytes;
      try (var body = response.body()) {
        bytes = body.readNBytes(65537);
      }
      if (bytes.length > 65536) {
        throw OAuthAuthenticationException.invalidExchange();
      }
      return accessToken(response.statusCode(), bytes);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw OAuthAuthenticationException.unavailable();
    } catch (IOException e) {
      throw OAuthAuthenticationException.unavailable();
    } catch (OAuthAuthenticationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw OAuthAuthenticationException.invalidExchange();
    }
  }

  private static String accessToken(int status, byte[] bytes) {
    if (status >= 500 || status == 429) {
      throw OAuthAuthenticationException.unavailable();
    }
    var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    requireSuccess(status, json);
    if (!json.has("token_type") || !"Bearer".equalsIgnoreCase(json.get("token_type").getAsString()) || !json.has("access_token")) {
      throw OAuthAuthenticationException.invalidExchange();
    }
    var exchanged = json.get("access_token").getAsString();
    if (exchanged.isBlank() || exchanged.length() > 16384) {
      throw OAuthAuthenticationException.invalidExchange();
    }
    return exchanged;
  }

  private static void requireSuccess(int status, JsonObject json) {
    if (status == 200) {
      return;
    }
    var error = json.has(ERROR_FIELD) && json.get(ERROR_FIELD).isJsonPrimitive() ? json.get(ERROR_FIELD).getAsString() : "";
    if (status == 400 && Set.of("invalid_grant", "invalid_token").contains(error)) {
      throw OAuthAuthenticationException.unauthorized();
    }
    throw OAuthAuthenticationException.invalidExchange();
  }

  private static String form(Map<String, String> fields) {
    return fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
      + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
  }
}


