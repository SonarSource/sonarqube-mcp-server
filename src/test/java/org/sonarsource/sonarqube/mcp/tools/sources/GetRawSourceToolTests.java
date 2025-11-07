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

import io.modelcontextprotocol.spec.McpSchema;
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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class GetRawSourceToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(GetRawSourceTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: key", true));
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to retrieve source code: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_file_is_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/NonExistent.php")).willReturn(aResponse().withStatus(404)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/NonExistent.php"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to retrieve source code: SonarQube answered with Error 404 on " +
          harness.getMockSonarQubeServer().baseUrl() + "/api/sources/raw?key=" + urlEncode("my_project:src/foo/NonExistent.php"), true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_only_key_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n// code here\n"
        }""".replace("<?php\n// code here\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_branch_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&branch=" + urlEncode("feature/my_branch"))
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "branch", "feature/my_branch"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_pull_request_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_both_branch_and_pull_request_parameters(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&branch=" + urlEncode("feature/my_branch") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "branch", "feature/my_branch", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to retrieve source code: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_file_is_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/NonExistent.php")).willReturn(aResponse().withStatus(404)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/NonExistent.php"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to retrieve source code: SonarQube answered with Error 404 on " +
          harness.getMockSonarQubeServer().baseUrl() + "/api/sources/raw?key=" + urlEncode("my_project:src/foo/NonExistent.php"), true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_only_key_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n// code here\n"
        }""".replace("<?php\n// code here\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_branch_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&branch=" + urlEncode("feature/my_branch"))
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "branch", "feature/my_branch"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_pull_request_parameter(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_raw_source_code_with_both_branch_and_pull_request_parameters(SonarQubeMcpServerTestHarness harness) {
      var sourceCode = """
        package org.sonar.check;

        public enum Priority {
        /**
          * WARNING : DO NOT CHANGE THE ENUMERATION ORDER
          * the enum ordinal is used for db persistence
          */
          BLOCKER, CRITICAL, MAJOR, MINOR, INFO
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_RAW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&branch=" + urlEncode("feature/my_branch") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(sourceCode)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetRawSourceTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "branch", "feature/my_branch", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "fileKey" : "my_project:src/foo/Bar.php",
          "sourceCode" : "<?php\n"
        }""".replace("<?php\n", sourceCode));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

  }

} 
