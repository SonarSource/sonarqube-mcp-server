/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.badges;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.MockWebServer;
import org.sonar.mcp.harness.ReceivedRequest;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonar.mcp.serverapi.badges.ProjectBadgesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class GetProjectMeasureBadgeToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_token_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and " +
          "'SONARQUBE_CLOUD_ORG'", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_org_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_TOKEN", "token"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and " +
          "'SONARQUBE_CLOUD_ORG'", true));
    }
  }

  @Nested
  class WithServer {

    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_request_fails(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ProjectBadgesApi.MEASURE_PATH + "?project=" + URLEncoder.encode("my-project", StandardCharsets.UTF_8) + "&metric=bugs")
        .willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/project_badges/measure?project=my-project&metric=bugs", true));
    }

    @SonarMcpServerTest
    void it_should_return_the_badge_svg(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ProjectBadgesApi.MEASURE_PATH + "?project=" + URLEncoder.encode("my-project", StandardCharsets.UTF_8) + "&metric=bugs")
        .willReturn(aResponse().withResponseBody(
          Body.fromOneOf(null, generateSvg(), null, null)
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(generateSvg(), false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarMcpServerTest
    void it_should_return_the_badge_svg_with_optional_parameters(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ProjectBadgesApi.MEASURE_PATH + "?project=" + URLEncoder.encode("my-project", StandardCharsets.UTF_8)
        + "&metric=bugs&branch=feature&token=secret")
        .willReturn(aResponse().withResponseBody(
          Body.fromOneOf(null, generateSvg(), null, null)
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        GetProjectMeasureBadgeTool.TOOL_NAME,
        Map.of(
          GetProjectMeasureBadgeTool.PROJECT_PROPERTY, "my-project",
          GetProjectMeasureBadgeTool.METRIC_PROPERTY, "bugs",
          GetProjectMeasureBadgeTool.BRANCH_PROPERTY, "feature",
          GetProjectMeasureBadgeTool.TOKEN_PROPERTY, "secret"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(generateSvg(), false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateSvg() {
    return """
        <svg xmlns="http://www.w3.org/2000/svg" height="20" width="142">
            <linearGradient id="b" x2="0" y2="100%">
                <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
                <stop offset="1" stop-opacity=".1"/>
            </linearGradient>
            <clipPath id="a">
                <rect width="142" height="20" rx="3" fill="#fff"/>
            </clipPath>
            <g clip-path="url(#a)">
                <rect fill="#555" height="20" width="109"/>
                <rect fill="#999" height="20" width="33" x="109"/>
                <rect fill="url(#b)" height="20" width="142"/>
            </g>
            <g fill="#fff" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11" text-anchor="left">
                <text x="26" y="15" textLength="77" fill="#010101" fill-opacity=".3">technical debt</text>
                <text x="26" y="14" textLength="77">technical debt</text>
                <text x="115" y="15" textLength="21" fill="#010101" fill-opacity=".3">21d</text>
                <text x="115" y="14" textLength="21">21d</text>
            </g>
            <g fill="#4e9bcd">
                <path d="M17.975 16.758h-.815c0-6.557-5.411-11.893-12.062-11.893v-.813c7.102 0 12.877 5.7 12.877 12.706z"/>
                <path d="M18.538 12.386c-.978-4.116-4.311-7.55-8.49-8.748l.186-.65c4.411 1.266 7.93 4.895 8.964 9.243zm.626-3.856a12.48 12.48 0 0 0-4.832-5.399l.282-.464a13.031 13.031 0 0 1 5.044 5.63z"/>
            </g>
        </svg>""";
  }

} 
