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
package org.sonarsource.sonarqube.mcp.tools.languages;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.languages.LanguagesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ListLanguagesToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListLanguagesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "languages":{
               "description":"List of supported programming languages",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "key":{
                        "type":"string",
                        "description":"Language key identifier"
                     },
                     "name":{
                        "type":"string",
                        "description":"Human-readable language name"
                     }
                  },
                  "required":[
                     "key",
                     "name"
                  ]
               }
            }
         },
         "required":[
            "languages"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListLanguagesTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListLanguagesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/languages/list", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListLanguagesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "languages" : [ {
            "key" : "c",
            "name" : "C"
          }, {
            "key" : "cpp",
            "name" : "C++"
          }, {
            "key" : "java",
            "name" : "Java"
          }, {
            "key" : "js",
            "name" : "JavaScript"
          }, {
            "key" : "python",
            "name" : "Python"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list_with_query(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH + "?q=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateFilteredPayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListLanguagesTool.TOOL_NAME,
        Map.of(ListLanguagesTool.QUERY_PROPERTY, "java"));

      assertResultEquals(result, """
        {
          "languages" : [ {
            "key" : "java",
            "name" : "Java"
          }, {
            "key" : "js",
            "name" : "JavaScript"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListLanguagesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListLanguagesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "languages" : [ {
            "key" : "c",
            "name" : "C"
          }, {
            "key" : "cpp",
            "name" : "C++"
          }, {
            "key" : "java",
            "name" : "Java"
          }, {
            "key" : "js",
            "name" : "JavaScript"
          }, {
            "key" : "python",
            "name" : "Python"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list_with_query(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(LanguagesApi.LIST_PATH + "?q=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateFilteredPayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ListLanguagesTool.TOOL_NAME,
        Map.of(ListLanguagesTool.QUERY_PROPERTY, "java"));

      assertResultEquals(result, """
        {
          "languages" : [ {
            "key" : "java",
            "name" : "Java"
          }, {
            "key" : "js",
            "name" : "JavaScript"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generatePayload() {
    return """
      {
        "languages": [
          {"key": "c", "name": "C"},
          {"key": "cpp", "name": "C++"},
          {"key": "java", "name": "Java"},
          {"key": "js", "name": "JavaScript"},
          {"key": "python", "name": "Python"}
        ]
      }""";
  }

  private static String generateFilteredPayload() {
    return """
      {
        "languages": [
          {"key": "java", "name": "Java"},
          {"key": "js", "name": "JavaScript"}
        ]
      }""";
  }
}
