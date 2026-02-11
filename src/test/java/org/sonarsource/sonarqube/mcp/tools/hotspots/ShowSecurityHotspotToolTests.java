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
package org.sonarsource.sonarqube.mcp.tools.hotspots;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ShowSecurityHotspotToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ShowSecurityHotspotTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();
  }

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SHOW_PATH + "?hotspot=AXJMFm6ERa2AinNL_0fP").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ShowSecurityHotspotTool.TOOL_NAME,
        Map.of(ShowSecurityHotspotTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_show_hotspot_details(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SHOW_PATH + "?hotspot=" + hotspotKey)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateHotspotDetails(hotspotKey).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ShowSecurityHotspotTool.TOOL_NAME,
        Map.of(ShowSecurityHotspotTool.HOTSPOT_KEY_PROPERTY, hotspotKey));

      assertResultEquals(result, """
        {
          "key" : "AXJMFm6ERa2AinNL_0fP",
          "component" : "com.example:project:src/main/java/com/example/Service.java",
          "project" : "com.example:project",
          "securityCategory" : "sql-injection",
          "vulnerabilityProbability" : "HIGH",
          "status" : "TO_REVIEW",
          "line" : 42,
          "message" : "Make sure using this hardcoded IP address is safe here.",
          "assignee" : "john.doe",
          "author" : "jane.smith",
          "creationDate" : "2023-05-13T17:55:39+0200",
          "updateDate" : "2023-05-14T10:20:15+0200",
          "textRange" : {
            "startLine" : 42,
            "endLine" : 42,
            "startOffset" : 15,
            "endOffset" : 30
          },
          "flows" : [ ],
          "comments" : [ ],
          "rule" : {
            "key" : "java:S1313",
            "name" : "IP addresses should not be hardcoded",
            "securityCategory" : "sql-injection",
            "vulnerabilityProbability" : "HIGH",
            "riskDescription" : "Hardcoded IP addresses can lead to security vulnerabilities.",
            "vulnerabilityDescription" : "Using hardcoded IP addresses makes the code less portable.",
            "fixRecommendations" : "Use configuration files or environment variables instead."
          },
          "canChangeStatus" : "true"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_show_hotspot_details(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SHOW_PATH + "?hotspot=" + hotspotKey)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateHotspotDetails(hotspotKey).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ShowSecurityHotspotTool.TOOL_NAME,
        Map.of(ShowSecurityHotspotTool.HOTSPOT_KEY_PROPERTY, hotspotKey));

      assertResultEquals(result, """
        {
          "key" : "AXJMFm6ERa2AinNL_0fP",
          "component" : "com.example:project:src/main/java/com/example/Service.java",
          "project" : "com.example:project",
          "securityCategory" : "sql-injection",
          "vulnerabilityProbability" : "HIGH",
          "status" : "TO_REVIEW",
          "line" : 42,
          "message" : "Make sure using this hardcoded IP address is safe here.",
          "assignee" : "john.doe",
          "author" : "jane.smith",
          "creationDate" : "2023-05-13T17:55:39+0200",
          "updateDate" : "2023-05-14T10:20:15+0200",
          "textRange" : {
            "startLine" : 42,
            "endLine" : 42,
            "startOffset" : 15,
            "endOffset" : 30
          },
          "flows" : [ ],
          "comments" : [ ],
          "rule" : {
            "key" : "java:S1313",
            "name" : "IP addresses should not be hardcoded",
            "securityCategory" : "sql-injection",
            "vulnerabilityProbability" : "HIGH",
            "riskDescription" : "Hardcoded IP addresses can lead to security vulnerabilities.",
            "vulnerabilityDescription" : "Using hardcoded IP addresses makes the code less portable.",
            "fixRecommendations" : "Use configuration files or environment variables instead."
          },
          "canChangeStatus" : "true"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateHotspotDetails(String hotspotKey) {
    return """
      {
        "key": "%s",
        "component": "com.example:project:src/main/java/com/example/Service.java",
        "project": "com.example:project",
        "securityCategory": "sql-injection",
        "vulnerabilityProbability": "HIGH",
        "status": "TO_REVIEW",
        "line": 42,
        "message": "Make sure using this hardcoded IP address is safe here.",
        "assignee": "john.doe",
        "author": "jane.smith",
        "creationDate": "2023-05-13T17:55:39+0200",
        "updateDate": "2023-05-14T10:20:15+0200",
        "textRange": {
          "startLine": 42,
          "endLine": 42,
          "startOffset": 15,
          "endOffset": 30
        },
        "flows": [],
        "comments": [],
        "changelog": [],
        "users": [],
        "rule": {
          "key": "java:S1313",
          "name": "IP addresses should not be hardcoded",
          "securityCategory": "sql-injection",
          "vulnerabilityProbability": "HIGH",
          "riskDescription": "Hardcoded IP addresses can lead to security vulnerabilities.",
          "vulnerabilityDescription": "Using hardcoded IP addresses makes the code less portable.",
          "fixRecommendations": "Use configuration files or environment variables instead."
        },
        "canChangeStatus": "true"
      }""".formatted(hotspotKey);
  }

}
