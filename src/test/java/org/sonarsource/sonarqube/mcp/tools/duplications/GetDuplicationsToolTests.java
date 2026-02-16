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
package org.sonarsource.sonarqube.mcp.tools.duplications;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.duplications.DuplicationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class GetDuplicationsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(GetDuplicationsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "duplications":{
               "description":"List of duplication groups found",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "blocks":{
                        "description":"List of code blocks involved in this duplication",
                        "type":"array",
                        "items":{
                           "type":"object",
                           "properties":{
                              "fileKey":{
                                 "type":"string",
                                 "description":"File key"
                              },
                              "fileName":{
                                 "type":"string",
                                 "description":"File name"
                              },
                              "from":{
                                 "type":"integer",
                                 "description":"Starting line number"
                              },
                              "size":{
                                 "type":"integer",
                                 "description":"Number of lines"
                              }
                           },
                           "required":[
                              "fileKey",
                              "fileName",
                              "from",
                              "size"
                           ]
                        }
                     }
                  },
                  "required":[
                     "blocks"
                  ]
               }
            },
            "files":{
               "description":"Map of file references to file information",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "key":{
                        "type":"string",
                        "description":"File key"
                     },
                     "name":{
                        "type":"string",
                        "description":"File name"
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
            "duplications",
            "files"
         ]
      }
      """);
  }

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(GetDuplicationsTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: Missing required argument: key").build());
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_file_is_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/NonExistent.php")).willReturn(aResponse().withStatus(404)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/NonExistent.php"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent("An error occurred during the tool execution: SonarQube answered with Error 404 on " +
          harness.getMockSonarQubeServer().baseUrl() + "/api/duplications/show?key=" + urlEncode("my_project:src/foo/NonExistent.php") + ". Please verify your token is valid and the requested resource exists.")
          .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_duplications_with_key_parameter(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [
            {
              "blocks": [
                {
                  "from": 94,
                  "size": 101,
                  "_ref": "1"
                },
                {
                  "from": 83,
                  "size": 101,
                  "_ref": "2"
                }
              ]
            },
            {
              "blocks": [
                {
                  "from": 38,
                  "size": 40,
                  "_ref": "1"
                },
                {
                  "from": 29,
                  "size": 39,
                  "_ref": "2"
                }
              ]
            }
          ],
          "files": {
            "1": {
              "key": "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarQube"
            },
            "2": {
              "key": "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarSource :: Orchestrator"
            }
          }
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertResultEquals(result, """
        {
          "duplications" : [ {
            "blocks" : [ {
              "from" : 94,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java"
            }, {
              "from" : 83,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java"
            } ]
          }, {
            "blocks" : [ {
              "from" : 38,
              "size" : 40,
              "fileName" : "CommandExecutor",
              "fileKey" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java"
            }, {
              "from" : 29,
              "size" : 39,
              "fileName" : "CommandExecutor",
              "fileKey" : "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java"
            } ]
          } ],
          "files" : [ {
            "key" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
            "name" : "CommandExecutor"
          }, {
            "key" : "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java",
            "name" : "CommandExecutor"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_duplications_when_no_duplications_found(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [],
          "files": {}
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Unique.php"))
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Unique.php"));

      assertResultEquals(result, """
        {
          "duplications" : [ ],
          "files" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_duplications_with_pull_request_parameter(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [
            {
              "blocks": [
                {
                  "from": 94,
                  "size": 101,
                  "_ref": "1"
                }
              ]
            }
          ],
          "files": {
            "1": {
              "key": "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarQube"
            }
          }
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "duplications" : [ {
            "blocks" : [ {
              "from" : 94,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java"
            } ]
          } ],
          "files" : [ {
            "key" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
            "name" : "CommandExecutor"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php")).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_file_is_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/NonExistent.php")).willReturn(aResponse().withStatus(404)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/NonExistent.php"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent("An error occurred during the tool execution: SonarQube answered with Error 404 on " +
          harness.getMockSonarQubeServer().baseUrl() + "/api/duplications/show?key=" + urlEncode("my_project:src/foo/NonExistent.php") + ". Please verify your token is valid and the requested resource exists.")
          .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_duplications_with_key_parameter(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [
            {
              "blocks": [
                {
                  "from": 94,
                  "size": 101,
                  "_ref": "1"
                },
                {
                  "from": 83,
                  "size": 101,
                  "_ref": "2"
                }
              ]
            }
          ],
          "files": {
            "1": {
              "key": "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarQube"
            },
            "2": {
              "key": "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarSource :: Orchestrator"
            }
          }
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php"))
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php"));

      assertResultEquals(result, """
        {
          "duplications" : [ {
            "blocks" : [ {
              "from" : 94,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java"
            }, {
              "from" : 83,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java"
            } ]
          } ],
          "files" : [ {
            "key" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
            "name" : "CommandExecutor"
          }, {
            "key" : "com.sonarsource.orchestrator:sonar-orchestrator:src/main/java/com/sonar/orchestrator/util/CommandExecutor.java",
            "name" : "CommandExecutor"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_duplications_when_no_duplications_found(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [],
          "files": {}
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Unique.php"))
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Unique.php"));

      assertResultEquals(result, """
        {
          "duplications" : [ ],
          "files" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_duplications_with_pull_request_parameter(SonarQubeMcpServerTestHarness harness) {
      var duplicationsJson = """
        {
          "duplications": [
            {
              "blocks": [
                {
                  "from": 94,
                  "size": 101,
                  "_ref": "1"
                }
              ]
            }
          ],
          "files": {
            "1": {
              "key": "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
              "name": "CommandExecutor",
              "projectName": "SonarQube"
            }
          }
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(DuplicationsApi.DUPLICATIONS_SHOW_PATH + "?key=" + urlEncode("my_project:src/foo/Bar.php") + "&pullRequest=5461")
        .willReturn(aResponse().withBody(duplicationsJson).withHeader("Content-Type", "application/json")));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetDuplicationsTool.TOOL_NAME,
        Map.of("key", "my_project:src/foo/Bar.php", "pullRequest", "5461"));

      assertResultEquals(result, """
        {
          "duplications" : [ {
            "blocks" : [ {
              "from" : 94,
              "size" : 101,
              "fileName" : "CommandExecutor",
              "fileKey" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java"
            } ]
          } ],
          "files" : [ {
            "key" : "org.codehaus.sonar:sonar-plugin-api:src/main/java/org/sonar/api/utils/command/CommandExecutor.java",
            "name" : "CommandExecutor"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

  }

}
