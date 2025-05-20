/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.MockWebServer;
import org.sonar.mcp.harness.ReceivedRequest;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

class ChangeIssuesStatusToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("status", new String[]{"accept"})));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Missing required argument: key", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_status_parameter_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Missing required argument: status", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_status_parameter_is_unknown(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k", "status", new String[]{"yolo"})));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Status is unknown: yolo", true));
    }

  }

  @Nested
  class WithServer {

    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[]{"accept"})));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to change the issue status: Forbidden", true));
    }

    @SonarMcpServerTest
    void it_should_change_the_status_to_accept(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[]{"accept"})));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", "issue=k&transition=accept"));
    }

    @SonarMcpServerTest
    void it_should_change_the_status_to_false_positive(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[]{"falsepositive"})));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", "issue=k&transition=falsepositive"));
    }

    @SonarMcpServerTest
    void it_should_reopen_the_issue(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[]{"reopen"})));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", "issue=k&transition=reopen"));
    }

  }

}
