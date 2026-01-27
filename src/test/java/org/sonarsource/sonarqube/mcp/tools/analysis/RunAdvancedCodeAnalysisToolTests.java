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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisHubApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class RunAdvancedCodeAnalysisToolTests {

  private static final Map<String, String> ADVANCED_ANALYSIS_ENV = Map.of(
    "SONARQUBE_ORG", "my-org",
    "SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true"
  );

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.annotations())
      .isNotNull()
      .satisfies(annotations -> {
        assertThat(annotations.readOnlyHint()).isTrue();
        assertThat(annotations.openWorldHint()).isTrue();
      });
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_if_organization_identifier_is_missing(SonarQubeMcpServerTestHarness harness) {
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}"
      ));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("An error occurred during the tool execution: Either organizationId or organizationKey is required")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_if_project_identifier_is_missing(SonarQubeMcpServerTestHarness harness) {
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "organizationKey", "my-org",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}"
      ));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("An error occurred during the tool execution: Either projectId or projectKey is required")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_return_issues_with_flows(SonarQubeMcpServerTestHarness harness) {
    stubAnalysisResponse(harness, RESPONSE_WITH_FLOWS);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "organizationKey", "my-org",
        "projectKey", "my-project",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "filePath" : "src/Main.java",
          "message" : "Null pointer dereference",
          "rule" : "java:S2259",
          "textRange" : {
            "startLine" : 10,
            "endLine" : 10
          },
          "flows" : [ {
            "type" : "DATA",
            "description" : "Data flow",
            "locations" : [ {
              "textRange" : {
                "startLine" : 5,
                "endLine" : 5
              },
              "message" : "Source of null",
              "file" : "src/Main.java"
            } ]
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_patch_result_when_provided(SonarQubeMcpServerTestHarness harness) {
    stubAnalysisResponse(harness, RESPONSE_WITH_PATCH_RESULT);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "organizationKey", "my-org",
        "projectKey", "my-project",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}",
        "patchContent", "++class Main2 {}"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ ],
        "patchResult" : {
          "newIssues" : [ {
            "id" : "new-issue-1",
            "filePath" : "src/Main.java",
            "message" : "New issue introduced",
            "rule" : "java:S1234"
          } ],
          "matchedIssues" : [ ],
          "closedIssues" : [ "old-issue-1" ]
        }
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_on_api_failure(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisHubApi.ANALYSES_PATH)
      .willReturn(aResponse().withStatus(403)));
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "organizationKey", "my-org",
        "projectKey", "my-project",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}"
      ));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_accept_organization_id_instead_of_key(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisHubApi.ANALYSES_PATH)
      .withRequestBody(containing("\"organizationId\":\"org-uuid\""))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(SIMPLE_RESPONSE)));
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "organizationId", "org-uuid",
        "projectKey", "my-project",
        "filePath", "src/Main.java",
        "fileContent", "class Main {}"
      ));

    assertThat(result.isError()).isFalse();
  }

  private static void stubAnalysisResponse(SonarQubeMcpServerTestHarness harness, String responseBody) {
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisHubApi.ANALYSES_PATH)
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody)));
  }

  private static final String SIMPLE_RESPONSE = """
    {
      "id": "analysis-1",
      "issues": [],
      "patchResult": null
    }
    """;

  private static final String RESPONSE_WITH_FLOWS = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "filePath": "src/Main.java",
          "message": "Null pointer dereference",
          "rule": "java:S2259",
          "textRange": {
            "startLine": 10,
            "endLine": 10,
            "startOffset": 4,
            "endOffset": 20
          },
          "flows": [
            {
              "type": "DATA",
              "description": "Data flow",
              "locations": [
                {
                  "textRange": {
                    "startLine": 5,
                    "endLine": 5,
                    "startOffset": 0,
                    "endOffset": 15
                  },
                  "message": "Source of null",
                  "file": "src/Main.java"
                }
              ]
            }
          ]
        }
      ],
      "patchResult": null
    }
    """;

  private static final String RESPONSE_WITH_PATCH_RESULT = """
    {
      "id": "analysis-1",
      "issues": [],
      "patchResult": {
        "newIssues": [
          {
            "id": "new-issue-1",
            "filePath": "src/Main.java",
            "message": "New issue introduced",
            "rule": "java:S1234",
            "textRange": null,
            "flows": []
          }
        ],
        "matchedIssues": [],
        "closedIssues": ["old-issue-1"]
      }
    }
    """;
}
