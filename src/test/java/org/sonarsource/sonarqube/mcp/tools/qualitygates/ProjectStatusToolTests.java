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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ProjectStatusToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ProjectStatusTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "conditions":{
               "description":"List of quality gate conditions with their pass/fail status",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "actualValue":{
                        "type":"string",
                        "description":"The current actual value of the metric"
                     },
                     "errorThreshold":{
                        "type":"string",
                        "description":"The threshold value that must not be exceeded"
                     },
                     "metricKey":{
                        "type":"string",
                        "description":"Metric key (e.g., new_coverage, new_blocker_violations)"
                     },
                     "status":{
                        "type":"string",
                        "description":"Condition status: OK if passed, ERROR if failed"
                     }
                  },
                  "required":[
                     "metricKey",
                     "status"
                  ]
               }
            },
            "failureDetails":{
               "type":"object",
               "properties":{
                  "coverageFailures":{
                     "description":"Coverage-related failures with list of files needing test coverage",
                     "type":"array",
                     "items":{
                        "type":"object",
                        "properties":{
                           "actualValue":{
                              "type":"string",
                              "description":"The actual coverage value"
                           },
                           "failedMetric":{
                              "type":"string",
                              "description":"The coverage metric that failed (e.g., new_coverage, coverage)"
                           },
                           "threshold":{
                              "type":"string",
                              "description":"The threshold that was not met"
                           },
                           "worstFiles":{
                              "description":"Top 10 files with lowest coverage (sorted worst first)",
                              "type":"array",
                              "items":{
                                 "type":"object",
                                 "properties":{
                                    "coverage":{
                                       "type":"string",
                                       "description":"Coverage percentage (0-100)"
                                    },
                                    "linesToCover":{
                                       "type":"integer",
                                       "description":"Number of lines to cover"
                                    },
                                    "path":{
                                       "type":"string",
                                       "description":"File path relative to project root"
                                    },
                                    "uncoveredLines":{
                                       "type":"integer",
                                       "description":"Number of uncovered lines"
                                    }
                                 },
                                 "required":[
                                    "path"
                                 ]
                              }
                           }
                        },
                        "required":[
                           "failedMetric",
                           "worstFiles"
                        ]
                     }
                  },
                  "dependencyRisksFailures":{
                     "description":"Dependency risk-related failures with list of vulnerable dependencies",
                     "type":"array",
                     "items":{
                        "type":"object",
                        "properties":{
                           "actualValue":{
                              "type":"string",
                              "description":"The actual number of risks"
                           },
                           "failedMetric":{
                              "type":"string",
                              "description":"The dependency risk metric that failed"
                           },
                           "risks":{
                              "description":"List of vulnerable dependencies",
                              "type":"array",
                              "items":{
                                 "type":"object",
                                 "properties":{
                                    "packageName":{
                                       "type":"string",
                                       "description":"Package name"
                                    },
                                    "severity":{
                                       "type":"string",
                                       "description":"Severity (BLOCKER, CRITICAL, HIGH, MEDIUM, LOW, INFO)"
                                    },
                                    "version":{
                                       "type":"string",
                                       "description":"Package version"
                                    },
                                    "vulnerabilityId":{
                                       "type":"string",
                                       "description":"CVE or vulnerability ID"
                                    }
                                 },
                                 "required":[
                                    "packageName",
                                    "severity",
                                    "version"
                                 ]
                              }
                           },
                           "threshold":{
                              "type":"string",
                              "description":"The threshold that was not met"
                           }
                        },
                        "required":[
                           "failedMetric",
                           "risks"
                        ]
                     }
                  },
                  "duplicationFailures":{
                     "description":"Duplication-related failures with list of most duplicated files",
                     "type":"array",
                     "items":{
                        "type":"object",
                        "properties":{
                           "actualValue":{
                              "type":"string",
                              "description":"The actual duplication value"
                           },
                           "failedMetric":{
                              "type":"string",
                              "description":"The duplication metric that failed (e.g., new_duplicated_lines_density)"
                           },
                           "threshold":{
                              "type":"string",
                              "description":"The threshold that was not met"
                           },
                           "worstFiles":{
                              "description":"Top 10 files with highest duplication (sorted worst first)",
                              "type":"array",
                              "items":{
                                 "type":"object",
                                 "properties":{
                                    "duplicatedBlocks":{
                                       "type":"integer",
                                       "description":"Number of duplicated blocks"
                                    },
                                    "duplicatedLines":{
                                       "type":"integer",
                                       "description":"Number of duplicated lines"
                                    },
                                    "path":{
                                       "type":"string",
                                       "description":"File path relative to project root"
                                    }
                                 },
                                 "required":[
                                    "path"
                                 ]
                              }
                           }
                        },
                        "required":[
                           "failedMetric",
                           "worstFiles"
                        ]
                     }
                  },
                  "hotspotsFailures":{
                     "description":"Security hotspot-related failures with list of unreviewed hotspots",
                     "type":"array",
                     "items":{
                        "type":"object",
                        "properties":{
                           "actualValue":{
                              "type":"string",
                              "description":"The actual number of hotspots"
                           },
                           "failedMetric":{
                              "type":"string",
                              "description":"The hotspot metric that failed (e.g., new_security_hotspots)"
                           },
                           "hotspots":{
                              "description":"List of unreviewed security hotspots",
                              "type":"array",
                              "items":{
                                 "type":"object",
                                 "properties":{
                                    "filePath":{
                                       "type":"string",
                                       "description":"File path"
                                    },
                                    "key":{
                                       "type":"string",
                                       "description":"Hotspot key"
                                    },
                                    "line":{
                                       "type":"integer",
                                       "description":"Line number where hotspot is located"
                                    },
                                    "message":{
                                       "type":"string",
                                       "description":"Hotspot message/description"
                                    },
                                    "vulnerabilityProbability":{
                                       "type":"string",
                                       "description":"Vulnerability probability (HIGH, MEDIUM, LOW)"
                                    }
                                 },
                                 "required":[
                                    "key",
                                    "message",
                                    "vulnerabilityProbability"
                                 ]
                              }
                           },
                           "threshold":{
                              "type":"string",
                              "description":"The threshold that was not met"
                           }
                        },
                        "required":[
                           "failedMetric",
                           "hotspots"
                        ]
                     }
                  },
                  "issuesFailures":{
                     "description":"Issue/violation-related failures with list of actual issues",
                     "type":"array",
                     "items":{
                        "type":"object",
                        "properties":{
                           "actualValue":{
                              "type":"string",
                              "description":"The actual number of issues"
                           },
                           "failedMetric":{
                              "type":"string",
                              "description":"The issue metric that failed (e.g., new_blocker_violations)"
                           },
                           "issues":{
                              "description":"List of issues that caused the failure",
                              "type":"array",
                              "items":{
                                 "type":"object",
                                 "properties":{
                                    "filePath":{
                                       "type":"string",
                                       "description":"File path"
                                    },
                                    "key":{
                                       "type":"string",
                                       "description":"Issue key"
                                    },
                                    "line":{
                                       "type":"integer",
                                       "description":"Line number where issue is located"
                                    },
                                    "message":{
                                       "type":"string",
                                       "description":"Issue message/description"
                                    },
                                    "rule":{
                                       "type":"string",
                                       "description":"Rule key (e.g., java:S1234)"
                                    },
                                    "severity":{
                                       "type":"string",
                                       "description":"Issue severity (BLOCKER, CRITICAL, MAJOR, MINOR, INFO)"
                                    },
                                    "type":{
                                       "type":"string",
                                       "description":"Issue type (BUG, VULNERABILITY, CODE_SMELL)"
                                    }
                                 },
                                 "required":[
                                    "key",
                                    "message",
                                    "rule",
                                    "severity"
                                 ]
                              }
                           },
                           "threshold":{
                              "type":"string",
                              "description":"The threshold that was not met"
                           }
                        },
                        "required":[
                           "failedMetric",
                           "issues"
                        ]
                     }
                  }
               },
               "description":"Detailed context about failed conditions (null if all passed or detailLevel=basic)"
            },
            "status":{
               "type":"string",
               "description":"Overall quality gate status: OK if passed, ERROR if failed, WARN for warnings"
            }
         },
         "required":[
            "conditions",
            "status"
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

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "nonexistent"));

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_no_parameter_is_provided(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ProjectStatusTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("projectKey");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/qualitygates/project_status?projectKey=pkey").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertResultEquals(result, """
        {
          "status" : "ERROR",
          "conditions" : [ {
            "metricKey" : "new_coverage",
            "status" : "ERROR",
            "errorThreshold" : "85",
            "actualValue" : "82.50562381034781"
          }, {
            "metricKey" : "new_blocker_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "14"
          }, {
            "metricKey" : "new_critical_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "1"
          }, {
            "metricKey" : "new_sqale_debt_ratio",
            "status" : "OK",
            "errorThreshold" : "5",
            "actualValue" : "0.6562109862671661"
          }, {
            "metricKey" : "reopened_issues",
            "status" : "OK",
            "actualValue" : "0"
          }, {
            "metricKey" : "open_issues",
            "status" : "ERROR",
            "actualValue" : "17"
          }, {
            "metricKey" : "skipped_tests",
            "status" : "OK",
            "actualValue" : "0"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key_and_pull_request(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey&pullRequest=5461")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey", ProjectStatusTool.PULL_REQUEST_PROPERTY, "5461"));

      assertResultEquals(result, """
        {
          "status" : "ERROR",
          "conditions" : [ {
            "metricKey" : "new_coverage",
            "status" : "ERROR",
            "errorThreshold" : "85",
            "actualValue" : "82.50562381034781"
          }, {
            "metricKey" : "new_blocker_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "14"
          }, {
            "metricKey" : "new_critical_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "1"
          }, {
            "metricKey" : "new_sqale_debt_ratio",
            "status" : "OK",
            "errorThreshold" : "5",
            "actualValue" : "0.6562109862671661"
          }, {
            "metricKey" : "reopened_issues",
            "status" : "OK",
            "actualValue" : "0"
          }, {
            "metricKey" : "open_issues",
            "status" : "ERROR",
            "actualValue" : "17"
          }, {
            "metricKey" : "skipped_tests",
            "status" : "OK",
            "actualValue" : "0"
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
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertResultEquals(result, """
        {
          "status" : "ERROR",
          "conditions" : [ {
            "metricKey" : "new_coverage",
            "status" : "ERROR",
            "errorThreshold" : "85",
            "actualValue" : "82.50562381034781"
          }, {
            "metricKey" : "new_blocker_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "14"
          }, {
            "metricKey" : "new_critical_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "1"
          }, {
            "metricKey" : "new_sqale_debt_ratio",
            "status" : "OK",
            "errorThreshold" : "5",
            "actualValue" : "0.6562109862671661"
          }, {
            "metricKey" : "reopened_issues",
            "status" : "OK",
            "actualValue" : "0"
          }, {
            "metricKey" : "open_issues",
            "status" : "ERROR",
            "actualValue" : "17"
          }, {
            "metricKey" : "skipped_tests",
            "status" : "OK",
            "actualValue" : "0"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key_and_pull_request(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey&pullRequest=5461")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey", ProjectStatusTool.PULL_REQUEST_PROPERTY, "5461"));

      assertResultEquals(result, """
        {
          "status" : "ERROR",
          "conditions" : [ {
            "metricKey" : "new_coverage",
            "status" : "ERROR",
            "errorThreshold" : "85",
            "actualValue" : "82.50562381034781"
          }, {
            "metricKey" : "new_blocker_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "14"
          }, {
            "metricKey" : "new_critical_violations",
            "status" : "ERROR",
            "errorThreshold" : "0",
            "actualValue" : "1"
          }, {
            "metricKey" : "new_sqale_debt_ratio",
            "status" : "OK",
            "errorThreshold" : "5",
            "actualValue" : "0.6562109862671661"
          }, {
            "metricKey" : "reopened_issues",
            "status" : "OK",
            "actualValue" : "0"
          }, {
            "metricKey" : "open_issues",
            "status" : "ERROR",
            "actualValue" : "17"
          }, {
            "metricKey" : "skipped_tests",
            "status" : "OK",
            "actualValue" : "0"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generatePayload() {
    return """
        {
        "projectStatus": {
          "status": "ERROR",
          "ignoredConditions": false,
          "conditions": [
            {
              "status": "ERROR",
              "metricKey": "new_coverage",
              "comparator": "LT",
              "periodIndex": 1,
              "errorThreshold": "85",
              "actualValue": "82.50562381034781"
            },
            {
              "status": "ERROR",
              "metricKey": "new_blocker_violations",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "0",
              "actualValue": "14"
            },
            {
              "status": "ERROR",
              "metricKey": "new_critical_violations",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "0",
              "actualValue": "1"
            },
            {
              "status": "OK",
              "metricKey": "new_sqale_debt_ratio",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "5",
              "actualValue": "0.6562109862671661"
            },
            {
              "status": "OK",
              "metricKey": "reopened_issues",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "0"
            },
            {
              "status": "ERROR",
              "metricKey": "open_issues",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "17"
            },
            {
              "status": "OK",
              "metricKey": "skipped_tests",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "0"
            }
          ],
          "periods": [
            {
              "index": 1,
              "mode": "last_version",
              "date": "2000-04-27T00:45:23+0200",
              "parameter": "2015-12-07"
            }
          ]
        }
      }""";
  }

}
