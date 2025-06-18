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
package org.sonarsource.sonarqube.mcp.tools.sources;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.sources.SourcesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class GetScmInfoToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(GetScmInfoTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: key", true));
    }

  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to retrieve SCM information: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_only_key_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "julien", "2013-03-14T13:17:22+0100", "b1e2b3e5d6f5"],
                [3, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | julien      | 2013-03-14T13:17:22+0100 | b1e2b3e5d6f5
          3    | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_commits_by_line_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&commits_by_line=true")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "julien", "2013-03-14T13:17:22+0100", "b1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "commits_by_line", "true"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | julien      | 2013-03-14T13:17:22+0100 | b1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_from_and_to_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&from=10&to=20")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [10, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [11, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "from", 10, "to", 20));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          10   | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          11   | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_all_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&commits_by_line=false&from=1&to=5")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "commits_by_line", "false", "from", 1, "to", 5));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_empty_scm_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Empty.php"))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": []
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Empty.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          No SCM information available for this file.""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to retrieve SCM information: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_only_key_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "julien", "2013-03-14T13:17:22+0100", "b1e2b3e5d6f5"],
                [3, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | julien      | 2013-03-14T13:17:22+0100 | b1e2b3e5d6f5
          3    | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_commits_by_line_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&commits_by_line=true")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "julien", "2013-03-14T13:17:22+0100", "b1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "commits_by_line", "true"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | julien      | 2013-03-14T13:17:22+0100 | b1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_from_and_to_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&from=10&to=20")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [10, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [11, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "from", 10, "to", 20));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          10   | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          11   | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_scm_information_with_all_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&commits_by_line=false&from=1&to=5")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": [
                [1, "julien", "2013-03-13T12:34:56+0100", "a1e2b3e5d6f5"],
                [2, "simon", "2014-01-01T15:35:36+0100", "c1e2b3e5d6f5"]
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "commits_by_line", "false", "from", 1, "to", 5));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          Line | Author      | Date                    | Revision
          -----|-------------|-------------------------|----------------
          1    | julien      | 2013-03-13T12:34:56+0100 | a1e2b3e5d6f5
          2    | simon       | 2014-01-01T15:35:36+0100 | c1e2b3e5d6f5""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_empty_scm_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_SCM_PATH + "?key=" + urlEncode("my_project:src/foo/Empty.php"))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "scm": []
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetScmInfoTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Empty.php"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SCM Information:
          ================

          No SCM information available for this file.""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }
}
