/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.organizations;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListOrganizationsToolTests {

  @SonarQubeMcpServerTest
  void it_should_be_registered_on_sonarqube_cloud_without_configured_org(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get("/api/organizations/search?member=true&p=1&ps=2").willReturn(okJson("""
      {"paging":{"pageIndex":1,"pageSize":2,"total":0},"organizations":[]}
      """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true"));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(ListOrganizationsTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_not_be_registered_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .isNotEmpty()
      .doesNotContain(ListOrganizationsTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_be_registered_on_sonarqube_cloud_even_when_org_is_configured(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "my-org"));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(ListOrganizationsTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_return_the_list_of_member_organizations(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get("/api/organizations/search?member=true&p=1&ps=2").willReturn(okJson("""
      {"paging":{"pageIndex":1,"pageSize":2,"total":0},"organizations":[]}
      """)));
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsApi.SEARCH_PATH + "?member=true&p=1&ps=100").willReturn(okJson("""
      {
        "paging":{"pageIndex":1,"pageSize":100,"total":2},
        "organizations":[
          {"key":"org-1","name":"Org One","description":"First","url":"https://one","avatar":"https://a1"},
          {"key":"org-2","name":"Org Two"}
        ]
      }
      """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true"));

    var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME);

    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {
        "organizations" : [ {
          "key" : "org-1",
          "name" : "Org One",
          "description" : "First",
          "url" : "https://one"
        }, {
          "key" : "org-2",
          "name" : "Org Two"
        } ],
        "paging" : {
          "pageIndex" : 1,
          "pageSize" : 100,
          "total" : 2,
          "hasNextPage" : false
        }
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_fail_when_page_size_is_invalid(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get("/api/organizations/search?member=true&p=1&ps=2").willReturn(okJson("""
      {"paging":{"pageIndex":1,"pageSize":2,"total":0},"organizations":[]}
      """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true"));

    var result = mcpClient.callTool(ListOrganizationsTool.TOOL_NAME, Map.of("pageSize", "501"));

    assertThat(result.isError()).isTrue();
    assertThat(result.content().getFirst().toString()).contains("Page size must be greater than 0 and less than or equal to 500");
  }

}
