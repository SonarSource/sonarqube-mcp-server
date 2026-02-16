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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchSecurityHotspotsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchSecurityHotspotsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "hotspots":{
               "description":"List of Security Hotspots found in the search",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "assignee":{
                        "type":"string",
                        "description":"User assigned to review the Security Hotspot"
                     },
                     "author":{
                        "type":"string",
                        "description":"Author who introduced the Security Hotspot"
                     },
                     "component":{
                        "type":"string",
                        "description":"Component (file) where the Security Hotspot is located"
                     },
                     "creationDate":{
                        "type":"string",
                        "description":"Date when the Security Hotspot was created"
                     },
                     "key":{
                        "type":"string",
                        "description":"Unique Security Hotspot identifier"
                     },
                     "line":{
                        "type":"integer",
                        "description":"Line number where the Security Hotspot is located"
                     },
                     "message":{
                        "type":"string",
                        "description":"Security Hotspot description message"
                     },
                     "project":{
                        "type":"string",
                        "description":"Project key where the Security Hotspot was found"
                     },
                     "resolution":{
                        "type":"string",
                        "description":"Resolution when status is REVIEWED (FIXED, SAFE, ACKNOWLEDGED)"
                     },
                     "ruleKey":{
                        "type":"string",
                        "description":"Rule key that triggered this Security Hotspot"
                     },
                     "securityCategory":{
                        "type":"string",
                        "description":"Security category (e.g., sql-injection, xss, weak-cryptography)"
                     },
                     "status":{
                        "type":"string",
                        "description":"Review status (TO_REVIEW, REVIEWED)"
                     },
                     "textRange":{
                        "type":"object",
                        "properties":{
                           "endLine":{
                              "type":"integer",
                              "description":"Ending line number"
                           },
                           "endOffset":{
                              "type":"integer",
                              "description":"Ending offset in the line"
                           },
                           "startLine":{
                              "type":"integer",
                              "description":"Starting line number"
                           },
                           "startOffset":{
                              "type":"integer",
                              "description":"Starting offset in the line"
                           }
                        },
                        "required":[
                           "endLine",
                           "endOffset",
                           "startLine",
                           "startOffset"
                        ],
                        "description":"Location of the Security Hotspot in the source file"
                     },
                     "updateDate":{
                        "type":"string",
                        "description":"Date when the Security Hotspot was last updated"
                     },
                     "vulnerabilityProbability":{
                        "type":"string",
                        "description":"Vulnerability probability (HIGH, MEDIUM, LOW)"
                     }
                  },
                  "required":[
                     "author",
                     "component",
                     "creationDate",
                     "key",
                     "message",
                     "project",
                     "securityCategory",
                     "status",
                     "updateDate",
                     "vulnerabilityProbability"
                  ]
               }
            },
            "paging":{
               "type":"object",
               "properties":{
                  "pageIndex":{
                     "type":"integer",
                     "description":"Current page index (1-based)"
                  },
                  "pageSize":{
                     "type":"integer",
                     "description":"Number of items per page"
                  },
                  "total":{
                     "type":"integer",
                     "description":"Total number of items across all pages"
                  }
               },
               "required":[
                  "pageIndex",
                  "pageSize",
                  "total"
               ],
               "description":"Pagination information for the results"
            }
         },
         "required":[
            "hotspots",
            "paging"
         ]
      }
      """);
  }

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_hotspots_for_a_project(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 1
                },
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot(hotspotKey)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "hotspots" : [ {
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
            "ruleKey" : "java:S1313"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_by_status(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project&status=REVIEWED")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 1
                },
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateReviewedHotspot(hotspotKey)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(
          SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project",
          SearchSecurityHotspotsTool.STATUS_PROPERTY, "REVIEWED"));

      assertResultEquals(result, """
        {
          "hotspots" : [ {
            "key" : "AXJMFm6ERa2AinNL_0fP",
            "component" : "com.example:project:src/main/java/com/example/Service.java",
            "project" : "com.example:project",
            "securityCategory" : "sql-injection",
            "vulnerabilityProbability" : "HIGH",
            "status" : "REVIEWED",
            "resolution" : "SAFE",
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
            "ruleKey" : "java:S1313"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_hotspots_with_pagination(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project&p=2&ps=50")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 2,
                  "pageSize": 50,
                  "total": 150
                },
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot(hotspotKey)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(
          SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project",
          SearchSecurityHotspotsTool.PAGE_PROPERTY, 2,
          SearchSecurityHotspotsTool.PAGE_SIZE_PROPERTY, 50));

      assertResultEquals(result, """
        {
          "hotspots" : [ {
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
            "ruleKey" : "java:S1313"
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 50,
            "total" : 150
          }
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_hotspots_for_a_project(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 1
                },
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot(hotspotKey)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "hotspots" : [ {
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
            "ruleKey" : "java:S1313"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_by_pull_request(SonarQubeMcpServerTestHarness harness) {
      var hotspotKey = "AXJMFm6ERa2AinNL_0fP";
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project&pullRequest=123")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 1
                },
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot(hotspotKey)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchSecurityHotspotsTool.TOOL_NAME,
        Map.of(
          SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, "my-project",
          SearchSecurityHotspotsTool.PULL_REQUEST_PROPERTY, "123"));

      assertResultEquals(result, """
        {
          "hotspots" : [ {
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
            "ruleKey" : "java:S1313"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
    }
  }

  private static String generateHotspot(String hotspotKey) {
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
          "ruleKey": "java:S1313",
          "flows": []
        }""".formatted(hotspotKey);
  }

  private static String generateReviewedHotspot(String hotspotKey) {
    return """
        {
          "key": "%s",
          "component": "com.example:project:src/main/java/com/example/Service.java",
          "project": "com.example:project",
          "securityCategory": "sql-injection",
          "vulnerabilityProbability": "HIGH",
          "status": "REVIEWED",
          "resolution": "SAFE",
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
          "ruleKey": "java:S1313",
          "flows": []
        }""".formatted(hotspotKey);
  }

}
