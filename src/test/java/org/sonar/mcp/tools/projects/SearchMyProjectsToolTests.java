/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.tools.projects;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonar.mcp.serverapi.projects.ProjectsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class SearchMyProjectsToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_token_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_org_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_TOKEN", "token"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'", true));
    }
  }

  @Nested
  class WithServer {

    private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());

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
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Make sure your token is valid.", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_failing(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ProjectsApi.SEARCH_MY_PROJECTS_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Error 500 on " + mockServer.baseUrl() + "/api/projects/search_my_projects", true));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_list(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ProjectsApi.SEARCH_MY_PROJECTS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "paging": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 2
              },
              "projects": [
                {
                  "key": "clang",
                  "name": "Clang",
                  "lastAnalysisDate": "2016-06-11T14:25:53+0000",
                  "qualityGate": "OK",
                  "links": []
                },
                {
                  "key": "net.java.openjdk:jdk7",
                  "name": "JDK 7",
                  "description": "JDK",
                  "lastAnalysisDate": "2016-06-10T13:17:53+0000",
                  "qualityGate": "ERROR",
                  "links": [
                    {
                      "name": "Sources",
                      "type": "scm",
                      "href": "http://download.java.net/openjdk/jdk8/"
                    }
                  ]
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 2 Sonar projects in your organization.
          Project key: clang | Project name: Clang
          Project key: net.java.openjdk:jdk7 | Project name: JDK 7
          """, false));
      assertThat(mockServer.getServeEvents().getRequests())
        .extracting(ServeEvent::getRequest)
        .extracting(LoggedRequest::getHeaders)
        .extracting(header -> header.getHeader("Authorization"))
        .extracting(HttpHeader::firstValue)
        .containsExactly("Bearer token");
    }
  }

}
