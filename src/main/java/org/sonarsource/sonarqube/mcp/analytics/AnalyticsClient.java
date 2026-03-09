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
package org.sonarsource.sonarqube.mcp.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class AnalyticsClient {

  static final String PROPERTY_ANALYTICS_ENDPOINT = "sonarqube.mcp.analytics.endpoint";
  static final String PROPERTY_ANALYTICS_API_KEY = "sonarqube.mcp.analytics.api.key";

  private static final String GESSIE_ENDPOINT = "https://events.sonardata.io/mcp";
  // Not a secret
  private static final String API_KEY = "1b8EU3XmRk5MKpIhqzKoD54LzfplrL2X1RLkLzLA";
  private static final String SOURCE_DOMAIN = "MCP";
  private static final int MAX_RETRIES = 2;
  private static final long RETRY_BASE_DELAY_MS = 2000L;
  private static final McpLogger LOG = McpLogger.getInstance();

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient;
  private final String endpoint;

  public AnalyticsClient(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.endpoint = System.getProperty(PROPERTY_ANALYTICS_ENDPOINT, GESSIE_ENDPOINT);
  }

  public void postEvent(AnalyticsEvent event) {
    String json;
    try {
      json = objectMapper.writeValueAsString(buildEnvelope(event));
    } catch (JsonProcessingException e) {
      LOG.debug("Failed to serialize analytics event: " + e.getMessage());
      return;
    }

    LOG.debug("Sending analytics event: type=" + event.eventType() + ", version=" + event.eventVersion());
    sendWithRetry(json, 0);
  }

  private void sendWithRetry(String json, int attempt) {
    httpClient.postAsync(endpoint, HttpClient.JSON_CONTENT_TYPE, json)
      .thenAccept(response -> {
        try (response) {
          if (response.isSuccessful()) {
            return;
          }
          // 4xx errors are not retried — they indicate a schema or auth problem
          if (response.code() >= 400 && response.code() < 500) {
            LOG.debug("Analytics event rejected (HTTP " + response.code() + "), not retrying: " + response.bodyAsString());
            return;
          }
          scheduleRetry(json, attempt, "HTTP " + response.code());
        }
      })
      .exceptionally(ex -> {
        scheduleRetry(json, attempt, ex.getMessage());
        return null;
      });
  }

  private void scheduleRetry(String json, int attempt, String reason) {
    if (attempt < MAX_RETRIES) {
      var nextAttempt = attempt + 1;
      var delayMs = RETRY_BASE_DELAY_MS * (long) Math.pow(2, attempt);
      LOG.debug("Analytics event failed (" + reason + "), retrying in " + delayMs + "ms (attempt " + nextAttempt + "/" + MAX_RETRIES + ")");
      CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> sendWithRetry(json, nextAttempt));
    } else {
      LOG.debug("Analytics event failed after " + MAX_RETRIES + " retries (" + reason + ")");
    }
  }

  private static AnalyticsEnvelope buildEnvelope(AnalyticsEvent event) {
    return new AnalyticsEnvelope(
      new AnalyticsEnvelope.Metadata(
        UUID.randomUUID().toString(),
        new AnalyticsEnvelope.Source(SOURCE_DOMAIN),
        event.eventType(),
        Long.toString(Instant.now().toEpochMilli()),
        event.eventVersion()
      ),
      event
    );
  }

  public static String resolveApiKey() {
    return System.getProperty(PROPERTY_ANALYTICS_API_KEY, API_KEY);
  }

}
