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
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class AnalyticsClient {

  static final String PROPERTY_ANALYTICS_ENDPOINT = "sonarqube.mcp.analytics.endpoint";
  static final String PROPERTY_ANALYTICS_API_KEY = "sonarqube.mcp.analytics.api.key";

  private static final String DEFAULT_PROD_ENDPOINT = "xxx";
  private static final String MOCK_API_KEY = "xxx";
  private static final String SOURCE_DOMAIN = "MCP";
  private static final McpLogger LOG = McpLogger.getInstance();

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient;
  private final String endpoint;

  public AnalyticsClient(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.endpoint = System.getProperty(PROPERTY_ANALYTICS_ENDPOINT, DEFAULT_PROD_ENDPOINT);
  }

  public void postEvent(AnalyticsEvent event) {
    var envelope = new AnalyticsEnvelope(
      new AnalyticsEnvelope.Metadata(
        UUID.randomUUID().toString(),
        new AnalyticsEnvelope.Source(SOURCE_DOMAIN),
        event.eventType(),
        Long.toString(Instant.now().toEpochMilli()),
        event.eventVersion()
      ),
      event
    );

    String json;
    try {
      json = objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException e) {
      LOG.debug("Failed to serialize analytics event: " + e.getMessage());
      return;
    }

    httpClient.postAsync(endpoint, HttpClient.JSON_CONTENT_TYPE, json)
      .thenAccept(response -> {
        try (response) {
          if (!response.isSuccessful()) {
            LOG.debug("Analytics event rejected by server: HTTP " + response.code() + " - " + response.bodyAsString());
          }
        }
      })
      .exceptionally(ex -> {
        LOG.debug("Failed to send analytics event: " + ex.getMessage());
        return null;
      });
  }

  public static String resolveApiKey() {
    return System.getProperty(PROPERTY_ANALYTICS_API_KEY, MOCK_API_KEY);
  }

}
