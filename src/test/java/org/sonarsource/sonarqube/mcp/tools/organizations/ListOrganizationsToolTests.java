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

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListOrganizationsToolTests {

  private static final Map<String, String> CLOUD_ENV = Map.of("SONARQUBE_ORG", "default-org", "SONARQUBE_TOOLSETS", "issues");

  @SonarQubeMcpServerTest
  void it_should_list_organization_metadata_and_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(okJson("""
        [{"id":"id-a","key":"org-a","name":"Organization A","uuidV4":"uuid-a"},
         {"id":"id-b","key":"org-b","name":"Organization B","uuidV4":"uuid-b"}]
        """)));
    var client = harness.newClient(CLOUD_ENV);
    var tool = client.listTools().stream().filter(t -> t.name().equals(ListOrganizationsTool.TOOL_NAME)).findFirst().orElseThrow();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.outputSchema()).isNull();
    assertThat(((Map<?, ?>) tool.inputSchema().get("properties"))).isEmpty();
    assertResultEquals(client.callTool(ListOrganizationsTool.TOOL_NAME), """
      {"organizations":[{"key":"org-a","name":"Organization A","id":"id-a","uuidV4":"uuid-a"},
                        {"key":"org-b","name":"Organization B","id":"id-b","uuidV4":"uuid-b"}]}
      """);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(OrganizationsApi.ORGANIZATIONS_PATH + "?excludeEligibility=true")).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organizationKey=")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_empty_list(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(okJson("[]")));
    assertResultEquals(harness.newClient(CLOUD_ENV).callTool(ListOrganizationsTool.TOOL_NAME), "{\"organizations\":[]}");
  }

  @SonarQubeMcpServerTest
  void it_should_report_forbidden(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(aResponse().withStatus(403)));
    var result = harness.newClient(CLOUD_ENV).callTool(ListOrganizationsTool.TOOL_NAME);
    assertThat(result.isError()).isTrue();
    assertThat(result.toString()).contains("Forbidden", "required permissions");
  }

  @SonarQubeMcpServerTest
  void it_should_report_server_errors(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(aResponse().withStatus(500)));
    var result = harness.newClient(CLOUD_ENV).callTool(ListOrganizationsTool.TOOL_NAME);
    assertThat(result.isError()).isTrue();
    assertThat(result.toString()).contains("Error 500");
  }

  @SonarQubeMcpServerTest
  void it_should_be_absent_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
    assertThat(harness.newClient().listTools()).noneMatch(tool -> tool.name().equals(ListOrganizationsTool.TOOL_NAME));
  }
}
