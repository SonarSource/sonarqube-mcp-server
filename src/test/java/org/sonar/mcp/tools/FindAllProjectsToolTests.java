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
package org.sonar.mcp.tools;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.mcp.harness.ProtobufUtils.protobufBody;

class FindAllProjectsToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_prefix_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Missing required argument: prefix", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_token_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_org_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_TOKEN", "token"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
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
    void it_should_return_an_error_if_the_request_fails(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Internal error.", true));
    }

    @SonarMcpServerTest
    void it_should_return_no_project_if_prefix_does_not_match(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get("/api/components/search.protobuf?qualifiers=TRK&organization=org&ps=500&p=1")
        .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
          .setPaging(Common.Paging.newBuilder().setTotal(0).build())
          .build()))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("No projects were found starting by prefix 'proj'.", false));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_list_if_prefix_matches(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get("/api/components/search.protobuf?qualifiers=TRK&organization=org&ps=500&p=1")
        .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
          .addComponents(Components.Component.newBuilder().setKey("projectKey").setName("projectName").build())
          .setPaging(Common.Paging.newBuilder().setTotal(1).build())
          .build()))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        "find_all_sonarqube_cloud_projects_starting_by",
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 Sonar projects in your organization starting by prefix 'proj'.
          Project key: projectKey | Project name: projectName
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
