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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

class AnalyticsClientTest {

  private WireMockServer wireMock;
  private HttpClientProvider httpClientProvider;
  private AnalyticsClient analyticsClient;
  private String originalEndpointProperty;

  @BeforeEach
  void setUp() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
    wireMock.stubFor(post(urlPathEqualTo("/")).willReturn(aResponse().withStatus(200)));

    httpClientProvider = new HttpClientProvider("SonarQube MCP Server Test");
    var httpClient = httpClientProvider.getHttpClientForAnalytics("test-api-key");

    originalEndpointProperty = System.getProperty(AnalyticsClient.PROPERTY_ANALYTICS_ENDPOINT);
    System.setProperty(AnalyticsClient.PROPERTY_ANALYTICS_ENDPOINT, wireMock.baseUrl() + "/");
    analyticsClient = new AnalyticsClient(httpClient);
  }

  @AfterEach
  void tearDown() {
    wireMock.stop();
    httpClientProvider.shutdown();
    if (originalEndpointProperty != null) {
      System.setProperty(AnalyticsClient.PROPERTY_ANALYTICS_ENDPOINT, originalEndpointProperty);
    } else {
      System.clearProperty(AnalyticsClient.PROPERTY_ANALYTICS_ENDPOINT);
    }
  }

  @Test
  void it_should_post_event_to_endpoint() {
    var event = new McpToolInvokedEvent(
      "invocation-uuid",
      "search_issues",
      "SQC",
      "org-uuid",
      null,
      "user-uuid",
      "server-uuid",
      "1.11.0.14345",
      "stdio",
      null,
      null,
      250L,
      true,
      null,
      1024L,
      null,
      System.currentTimeMillis()
    );

    analyticsClient.postEvent(event);

    await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(postRequestedFor(urlPathEqualTo("/")))
    );
  }

  @Test
  void it_should_wrap_event_in_gessie_envelope() throws Exception {
    var event = new McpToolInvokedEvent(
      "inv-1",
      "search_issues",
      "SQS",
      null,
      "install-123",
      null,
      "srv-uuid",
      "1.11.0.14345",
      "http",
      "cursor",
      "1.0.0",
      512L,
      false,
      "not_found",
      2048L,
      "amd64",
      1000L
    );

    analyticsClient.postEvent(event);

    await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(postRequestedFor(urlPathEqualTo("/")))
    );

    var receivedRequest = wireMock.getAllServeEvents().getFirst().getRequest();
    var mapper = new ObjectMapper();
    var body = (ObjectNode) mapper.readTree(receivedRequest.getBodyAsString());

    // Normalize the two dynamic fields before string comparison
    ((ObjectNode) body.get("metadata")).put("event_id", "<event_id>");
    ((ObjectNode) body.get("metadata")).put("event_timestamp", "<event_timestamp>");

    var expectedJson = """
      {
        "metadata": {
          "event_id": "<event_id>",
          "source": {"domain": "MCP"},
          "event_type": "Analytics.Mcp.McpToolInvoked",
          "event_timestamp": "<event_timestamp>",
          "event_version": "1"
        },
        "event_payload": {
          "invocation_id": "inv-1",
          "tool_name": "search_issues",
          "connection_type": "SQS",
          "sqs_installation_id": "install-123",
          "mcp_server_id": "srv-uuid",
          "mcp_server_version": "1.11.0.14345",
          "transport_mode": "http",
          "calling_agent_name": "cursor",
          "calling_agent_version": "1.0.0",
          "tool_execution_duration_ms": 512,
          "is_successful": false,
          "error_type": "not_found",
          "response_size_bytes": 2048,
          "container_arch": "amd64",
          "invocation_timestamp": 1000
        }
      }
      """;

    assertThat(mapper.writeValueAsString(body))
      .isEqualTo(mapper.writeValueAsString(mapper.readTree(expectedJson)));
  }

  @Test
  void it_should_send_x_api_key_header() {
    var event = new McpToolInvokedEvent("id", "tool", "SQS", null, null, null, "srv", "1.0.0", "stdio", null, null, 100L, true, null, 0L, null, 0L);

    analyticsClient.postEvent(event);

    await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
        .withHeader("x-api-key", WireMock.equalTo("test-api-key")))
    );
  }

  @Test
  void it_should_retry_up_to_twice_on_server_error() {
    wireMock.stubFor(post(urlPathEqualTo("/")).willReturn(aResponse().withStatus(500)));

    var event = new McpToolInvokedEvent("id", "tool", "SQS", null, null, null, "srv", "1.0.0", "stdio", null, null, 100L, true, null, 0L, null, 0L);
    analyticsClient.postEvent(event);

    // 1 initial attempt + 2 retries = 3 total
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(3, postRequestedFor(urlPathEqualTo("/")))
    );
  }

  @Test
  void it_should_not_retry_on_client_error() {
    wireMock.stubFor(post(urlPathEqualTo("/")).willReturn(aResponse().withStatus(400)));

    var event = new McpToolInvokedEvent("id", "tool", "SQS", null, null, null, "srv", "1.0.0", "stdio", null, null, 100L, true, null, 0L, null, 0L);
    analyticsClient.postEvent(event);

    await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(1, postRequestedFor(urlPathEqualTo("/")))
    );
  }

  @Test
  void it_should_succeed_after_transient_failures() {
    wireMock.stubFor(post(urlPathEqualTo("/"))
      .inScenario("retry")
      .whenScenarioStateIs(STARTED)
      .willReturn(aResponse().withStatus(503))
      .willSetStateTo("second-attempt"));
    wireMock.stubFor(post(urlPathEqualTo("/"))
      .inScenario("retry")
      .whenScenarioStateIs("second-attempt")
      .willReturn(aResponse().withStatus(200)));

    var event = new McpToolInvokedEvent("id", "tool", "SQS", null, null, null, "srv", "1.0.0", "stdio", null, null, 100L, true, null, 0L, null, 0L);
    analyticsClient.postEvent(event);

    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
      wireMock.verify(2, postRequestedFor(urlPathEqualTo("/")))
    );
  }

  @Test
  void it_should_silently_swallow_errors_when_server_is_unreachable() {
    var httpClient = httpClientProvider.getHttpClientForAnalytics("key");
    System.setProperty(AnalyticsClient.PROPERTY_ANALYTICS_ENDPOINT, "http://192.0.2.1:9999/mcp");
    var unreachableClient = new AnalyticsClient(httpClient);

    var event = new McpToolInvokedEvent("id", "tool", "SQS", null, null, null, "srv", "1.0.0", "stdio", null, null, 100L, true, null, 0L, null, 0L);

    assertThatCode(() -> unreachableClient.postEvent(event)).doesNotThrowAnyException();
  }

}
