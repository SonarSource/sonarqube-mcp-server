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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
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

class GetComponentMeasuresToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));
      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("No component found.", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_branch(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&branch=main&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.BRANCH_PROPERTY, "main"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"ncloc", "complexity"}
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.PULL_REQUEST_PROPERTY, "123"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: EmptyFile.java
        Key: MY_PROJECT:EmptyFile.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/EmptyFile.java

        No measures found for this component.
        Available Metrics:
          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false""", false));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("No component found.", false));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_component_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_component_measures_with_branch(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=" + urlEncode("MY_PROJECT:ElementImpl.java") + "&branch=main&additionalFields=metrics")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateComponentMeasuresResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.BRANCH_PROPERTY, "main"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.METRIC_KEYS_PROPERTY, new String[]{"ncloc", "complexity"}
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of(
          GetComponentMeasuresTool.COMPONENT_PROPERTY, "MY_PROJECT:ElementImpl.java",
          GetComponentMeasuresTool.PULL_REQUEST_PROPERTY, "123"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: ElementImpl.java
        Key: MY_PROJECT:ElementImpl.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/com/sonarsource/markdown/impl/ElementImpl.java

        Measures:
          - Complexity (complexity): 12
            Description: Cyclomatic complexity
          - New issues (new_violations):  | New: 25 (not best)
            Description: New Issues
          - Lines of code (ncloc): 114
            Description: Non Commenting Lines of Code

        Available Metrics:
          - Complexity (complexity)
            Description: Cyclomatic complexity
            Domain: Complexity
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false

          - New issues (new_violations)
            Description: New Issues
            Domain: Issues
            Type: INT
            Higher values are better: false
            Qualitative: true
            Hidden: false
            Custom: false

        Periods:
          - Period 1: previous_version (2016-01-11T10:49:50+0100) - 1.0-SNAPSHOT""", false));
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

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetComponentMeasuresTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
        Component: EmptyFile.java
        Key: MY_PROJECT:EmptyFile.java
        Qualifier: FIL
        Language: java
        Path: src/main/java/EmptyFile.java

        No measures found for this component.
        Available Metrics:
          - Lines of code (ncloc)
            Description: Non Commenting Lines of Code
            Domain: Size
            Type: INT
            Higher values are better: false
            Qualitative: false
            Hidden: false
            Custom: false""", false));
    }

  }

  private static String generateComponentMeasuresResponse() {
    return """
      {
        "component": {
          "key": "MY_PROJECT:ElementImpl.java",
          "name": "ElementImpl.java",
          "qualifier": "FIL",
          "language": "java",
          "path": "src/main/java/com/sonarsource/markdown/impl/ElementImpl.java",
          "measures": [
            {
              "metric": "complexity",
              "value": "12"
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
              "value": "114"
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
