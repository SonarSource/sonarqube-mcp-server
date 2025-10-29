/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.tools.issues;

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

class ChangeIssuesStatusToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("status", new String[] {"accept"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: key", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_status_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: status", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_status_parameter_is_unknown(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of(
          "key", "k",
          "status", new String[] {"yolo"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Status is unknown: yolo", true));
    }

  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/issues/do_transition").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[] {"accept"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_change_the_status_to_accept(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[] {"accept"}));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "issue=k&transition=accept"));
    }

    @SonarQubeMcpServerTest
    void it_should_change_the_status_to_false_positive(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[] {"falsepositive"}));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "issue=k&transition=falsepositive"));
    }

    @SonarQubeMcpServerTest
    void it_should_reopen_the_issue(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post("/api/issues/do_transition").willReturn(ok()));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ChangeIssueStatusTool.TOOL_NAME,
        Map.of("key", "k",
          "status", new String[] {"reopen"}));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("The issue status was successfully changed.", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "issue=k&transition=reopen"));
    }

  }

}
