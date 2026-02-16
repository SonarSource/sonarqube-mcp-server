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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.measures.MeasuresApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class GetComponentMeasuresToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(GetComponentMeasuresTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "component":{
               "type":"object",
               "properties":{
                  "description":{
                     "type":"string",
                     "description":"Component description"
                  },
                  "key":{
                     "type":"string",
                     "description":"Component key"
                  },
                  "language":{
                     "type":"string",
                     "description":"Programming language"
                  },
                  "name":{
                     "type":"string",
                     "description":"Component display name"
                  },
                  "path":{
                     "type":"string",
                     "description":"Component path"
                  },
                  "qualifier":{
                     "type":"string",
                     "description":"Component qualifier (TRK for project, FIL for file, etc.)"
                  }
               },
               "required":[
                  "key",
                  "name",
                  "qualifier"
               ],
               "description":"Component information"
            },
            "measures":{
               "description":"List of measures for the component",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "metric":{
                        "type":"string",
                        "description":"Metric key"
                     },
                     "value":{
                        "type":"string",
                        "description":"Measure value"
                     }
                  },
                  "required":[
                     "metric"
                  ]
               }
            },
            "metrics":{
               "description":"Metadata about the metrics",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "custom":{
                        "type":"boolean",
                        "description":"Whether this is a custom metric"
                     },
                     "description":{
                        "type":"string",
                        "description":"Metric description"
                     },
                     "domain":{
                        "type":"string",
                        "description":"Metric domain/category"
                     },
                     "hidden":{
                        "type":"boolean",
                        "description":"Whether the metric is hidden"
                     },
                     "key":{
                        "type":"string",
                        "description":"Metric key"
                     },
                     "name":{
                        "type":"string",
                        "description":"Metric display name"
                     },
                     "type":{
                        "type":"string",
                        "description":"Metric value type"
                     }
                  },
                  "required":[
                     "custom",
                     "description",
                     "domain",
                     "hidden",
                     "key",
                     "name",
                     "type"
                  ]
               }
            }
         },
         "required":[
            "component",
            "measures"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_succeed_when_no_component_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": null,
            "metrics": [],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "",
            "name" : "",
            "qualifier" : ""
          },
          "measures" : [ ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_component_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java"));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_metric_keys(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&metricKeys=ncloc,complexity&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"ncloc", "complexity"}
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_pull_request(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&pullRequest=123&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.PULL_REQUEST_PROPERTY, "123"
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_component_with_no_measures(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": {
              "key": "MY_PROJECT:EmptyFile.java",
              "name": "EmptyFile.java",
              "qualifier": "FIL",
              "language": "java",
              "path": "src/main/java/EmptyFile.java",
              "measures": []
            },
            "metrics": [
              {
                "key": "ncloc",
                "name": "Lines of code",
                "description": "Non Commenting Lines of Code",
                "domain": "Size",
                "type": "INT",
                "higherValuesAreBetter": false,
                "qualitative": false,
                "hidden": false,
                "custom": false
              }
            ],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:EmptyFile.java",
            "name" : "EmptyFile.java",
            "qualifier" : "FIL",
            "language" : "java",
            "path" : "src/main/java/EmptyFile.java"
          },
          "measures" : [ ],
          "metrics" : [ {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_measures_with_and_without_bestValue(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("org.sonarsource.sonarlint.core:sonarlint-core-parent") + "&metricKeys=coverage,ncloc&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": {
              "key": "org.sonarsource.sonarlint.core:sonarlint-core-parent",
              "name": "SonarLint Core",
              "description": "Library used by SonarLint flavors (Eclipse, IntelliJ, VSCode...)",
              "qualifier": "TRK",
              "measures": [
                {
                  "metric": "coverage",
                  "value": "91.9",
                  "bestValue": false
                },
                {
                  "metric": "ncloc",
                  "value": "53717"
                }
              ]
            },
            "metrics": [
              {
                "key": "coverage",
                "name": "Coverage",
                "description": "Coverage by tests",
                "domain": "Coverage",
                "type": "PERCENT",
                "higherValuesAreBetter": true,
                "qualitative": true,
                "hidden": false,
                "custom": false
              },
              {
                "key": "ncloc",
                "name": "Lines of code",
                "description": "Non Commenting Lines of Code",
                "domain": "Size",
                "type": "INT",
                "higherValuesAreBetter": false,
                "qualitative": false,
                "hidden": false,
                "custom": false
              }
            ],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "org.sonarsource.sonarlint.core:sonarlint-core-parent",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"coverage", "ncloc"}
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "org.sonarsource.sonarlint.core:sonarlint-core-parent",
            "name" : "SonarLint Core",
            "qualifier" : "TRK",
            "description" : "Library used by SonarLint flavors (Eclipse, IntelliJ, VSCode...)"
          },
          "measures" : [ {
            "metric" : "coverage",
            "value" : "91.9"
          }, {
            "metric" : "ncloc",
            "value" : "53717"
          } ],
          "metrics" : [ {
            "key" : "coverage",
            "name" : "Coverage",
            "description" : "Coverage by tests",
            "domain" : "Coverage",
            "type" : "PERCENT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_succeed_when_no_component_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": null,
            "metrics": [],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "",
            "name" : "",
            "qualifier" : ""
          },
          "measures" : [ ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_component_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java"));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_metric_keys(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&metricKeys=ncloc,complexity&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"ncloc", "complexity"}
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_pull_request(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&pullRequest=123&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.PULL_REQUEST_PROPERTY, "123"
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:ElementImpl.java",
            "name" : "ElementImpl.java",
            "qualifier" : "FIL",
            "description" : "Implementation of Element interface",
            "language" : "java",
            "path" : "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java"
          },
          "measures" : [ {
            "metric" : "complexity",
            "value" : "12"
          }, {
            "metric" : "new_violations"
          }, {
            "metric" : "ncloc",
            "value" : "114"
          } ],
          "metrics" : [ {
            "key" : "complexity",
            "name" : "Complexity",
            "description" : "Cyclomatic complexity",
            "domain" : "Complexity",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "new_violations",
            "name" : "New issues",
            "description" : "New Issues",
            "domain" : "Issues",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_component_with_no_measures(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": {
              "key": "MY_PROJECT:EmptyFile.java",
              "name": "EmptyFile.java",
              "qualifier": "FIL",
              "language": "java",
              "path": "src/main/java/EmptyFile.java",
              "measures": []
            },
            "metrics": [
              {
                "key": "ncloc",
                "name": "Lines of code",
                "description": "Non Commenting Lines of Code",
                "domain": "Size",
                "type": "INT",
                "higherValuesAreBetter": false,
                "qualitative": false,
                "hidden": false,
                "custom": false
              }
            ],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(GetComponentMeasuresTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "MY_PROJECT:EmptyFile.java",
            "name" : "EmptyFile.java",
            "qualifier" : "FIL",
            "language" : "java",
            "path" : "src/main/java/EmptyFile.java"
          },
          "measures" : [ ],
          "metrics" : [ {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_measures_with_and_without_bestValue(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("org.sonarsource.sonarlint.core:sonarlint-core-parent") + "&metricKeys=coverage,ncloc&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
          {
            "component": {
              "key": "org.sonarsource.sonarlint.core:sonarlint-core-parent",
              "name": "SonarLint Core",
              "description": "Library used by SonarLint flavors (Eclipse, IntelliJ, VSCode...)",
              "qualifier": "TRK",
              "measures": [
                {
                  "metric": "coverage",
                  "value": "91.9",
                  "bestValue": false
                },
                {
                  "metric": "ncloc",
                  "value": "53717"
                }
              ]
            },
            "metrics": [
              {
                "key": "coverage",
                "name": "Coverage",
                "description": "Coverage by tests",
                "domain": "Coverage",
                "type": "PERCENT",
                "higherValuesAreBetter": true,
                "qualitative": true,
                "hidden": false,
                "custom": false
              },
              {
                "key": "ncloc",
                "name": "Lines of code",
                "description": "Non Commenting Lines of Code",
                "domain": "Size",
                "type": "INT",
                "higherValuesAreBetter": false,
                "qualitative": false,
                "hidden": false,
                "custom": false
              }
            ],
            "periods": []
          }
          """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.PROJECT_KEY_PROPERTY, "org.sonarsource.sonarlint.core:sonarlint-core-parent",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"coverage", "ncloc"}
        ));

      assertResultEquals(result, """
        {
          "component" : {
            "key" : "org.sonarsource.sonarlint.core:sonarlint-core-parent",
            "name" : "SonarLint Core",
            "qualifier" : "TRK",
            "description" : "Library used by SonarLint flavors (Eclipse, IntelliJ, VSCode...)"
          },
          "measures" : [ {
            "metric" : "coverage",
            "value" : "91.9"
          }, {
            "metric" : "ncloc",
            "value" : "53717"
          } ],
          "metrics" : [ {
            "key" : "coverage",
            "name" : "Coverage",
            "description" : "Coverage by tests",
            "domain" : "Coverage",
            "type" : "PERCENT",
            "hidden" : false,
            "custom" : false
          }, {
            "key" : "ncloc",
            "name" : "Lines of code",
            "description" : "Non Commenting Lines of Code",
            "domain" : "Size",
            "type" : "INT",
            "hidden" : false,
            "custom" : false
          } ]
        }""");
    }

  }

  private static String generateComponentMeasuresResponse() {
    return """
      {
        "component": {
          "key": "MY_PROJECT:ElementImpl.java",
          "name": "ElementImpl.java",
          "description": "Implementation of Element interface",
          "qualifier": "FIL",
          "language": "java",
          "path": "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java",
          "measures": [
            {
              "metric": "complexity",
              "value": "12",
              "bestValue": false
            },
            {
              "metric": "new_violations",
              "periods": [
                {
                  "index": 1,
                  "value": "25",
                  "bestValue": false
                }
              ]
            },
            {
              "metric": "ncloc",
              "value": "114",
              "bestValue": false
            }
          ]
        },
        "metrics": [
          {
            "key": "complexity",
            "name": "Complexity",
            "description": "Cyclomatic complexity",
            "domain": "Complexity",
            "type": "INT",
            "higherValuesAreBetter": false,
            "qualitative": false,
            "hidden": false,
            "custom": false
          },
          {
            "key": "ncloc",
            "name": "Lines of code",
            "description": "Non Commenting Lines of Code",
            "domain": "Size",
            "type": "INT",
            "higherValuesAreBetter": false,
            "qualitative": false,
            "hidden": false,
            "custom": false
          },
          {
            "key": "new_violations",
            "name": "New issues",
            "description": "New Issues",
            "domain": "Issues",
            "type": "INT",
            "higherValuesAreBetter": false,
            "qualitative": true,
            "hidden": false,
            "custom": false
          }
        ],
        "periods": [
          {
            "index": 1,
            "mode": "previous_version",
            "date": "2016-01-11T10:49:50+0100",
            "parameter": "1.0-SNAPSHOT"
          }
        ]
      }
      """;
  }

} 
