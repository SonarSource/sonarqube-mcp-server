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
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ChangeSecurityHotspotStatusToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ChangeSecurityHotspotStatusTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().readOnlyHint()).isFalse();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();
  }

  @Nested
  class ValidationTests {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_resolution_is_missing_when_status_is_reviewed(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"REVIEWED"}));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
        .addTextContent("Resolution is required when status is REVIEWED. Valid resolutions: FIXED, SAFE, ACKNOWLEDGED").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_resolution_is_provided_when_status_is_to_review(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"TO_REVIEW"},
          ChangeSecurityHotspotStatusTool.RESOLUTION_PROPERTY, "SAFE"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
        .addTextContent("Resolution should not be provided when status is TO_REVIEW").build());
    }
  }

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/hotspots/change_status").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"TO_REVIEW"}));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
        .addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.")
        .build());
    }

    @SonarQubeMcpServerTest
    void it_should_change_status_to_reviewed_with_fixed_resolution(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/hotspots/change_status").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"REVIEWED"},
          ChangeSecurityHotspotStatusTool.RESOLUTION_PROPERTY, "FIXED",
          ChangeSecurityHotspotStatusTool.COMMENT_PROPERTY, "Implemented fix using environment variables"));

      assertResultEquals(result, """
        {
          "success" : true,
          "message" : "The Security Hotspot status was successfully changed.",
          "hotspotKey" : "AXJMFm6ERa2AinNL_0fP",
          "newStatus" : "REVIEWED",
          "newResolution" : "FIXED"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "hotspot=AXJMFm6ERa2AinNL_0fP&status=REVIEWED&resolution=FIXED&comment=Implemented+fix+using+environment+variables"));
    }

    @SonarQubeMcpServerTest
    void it_should_change_status_to_reviewed_with_safe_resolution(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/hotspots/change_status").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"REVIEWED"},
          ChangeSecurityHotspotStatusTool.RESOLUTION_PROPERTY, "SAFE"));

      assertResultEquals(result, """
        {
          "success" : true,
          "message" : "The Security Hotspot status was successfully changed.",
          "hotspotKey" : "AXJMFm6ERa2AinNL_0fP",
          "newStatus" : "REVIEWED",
          "newResolution" : "SAFE"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "hotspot=AXJMFm6ERa2AinNL_0fP&status=REVIEWED&resolution=SAFE"));
    }

    @SonarQubeMcpServerTest
    void it_should_change_status_to_to_review(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/hotspots/change_status").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"TO_REVIEW"}));

      assertResultEquals(result, """
        {
          "success" : true,
          "message" : "The Security Hotspot status was successfully changed.",
          "hotspotKey" : "AXJMFm6ERa2AinNL_0fP",
          "newStatus" : "TO_REVIEW"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "hotspot=AXJMFm6ERa2AinNL_0fP&status=TO_REVIEW"));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_change_status_to_reviewed_with_acknowledged_resolution(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/hotspots/change_status").willReturn(ok()));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeSecurityHotspotStatusTool.TOOL_NAME,
        Map.of(
          ChangeSecurityHotspotStatusTool.HOTSPOT_KEY_PROPERTY, "AXJMFm6ERa2AinNL_0fP",
          ChangeSecurityHotspotStatusTool.STATUS_PROPERTY, new String[] {"REVIEWED"},
          ChangeSecurityHotspotStatusTool.RESOLUTION_PROPERTY, "ACKNOWLEDGED",
          ChangeSecurityHotspotStatusTool.COMMENT_PROPERTY, "Accepted as acceptable risk"));

      assertResultEquals(result, """
        {
          "success" : true,
          "message" : "The Security Hotspot status was successfully changed.",
          "hotspotKey" : "AXJMFm6ERa2AinNL_0fP",
          "newStatus" : "REVIEWED",
          "newResolution" : "ACKNOWLEDGED"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "hotspot=AXJMFm6ERa2AinNL_0fP&status=REVIEWED&resolution=ACKNOWLEDGED&comment=Accepted+as+acceptable+risk"));
    }
  }

}
