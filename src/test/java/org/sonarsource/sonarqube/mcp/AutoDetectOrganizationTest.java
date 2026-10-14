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
package org.sonarsource.sonarqube.mcp;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoDetectOrganizationTest {

  private static final String AUTO_ORG_KEY = "auto-org";
  private static final String AUTO_ORG_UUID = "00000000-0000-0000-0000-000000000009";
  private static final Map<String, String> CLOUD_WITHOUT_ORG = Map.of(
    "SONARQUBE_IS_CLOUD", "true",
    "SONARQUBE_TOOLSETS", "issues");

  @SonarQubeMcpServerTest
  void it_should_apply_sonarqube_org_to_startup_server_api(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_ORG", AUTO_ORG_KEY,
      "SONARQUBE_TOOLSETS", "issues"));

    assertThat(mcpClient.listTools()).isNotEmpty();
    mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&organization=" + AUTO_ORG_KEY)).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH + "?excludeEligibility=true")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_auto_select_the_single_organization_of_the_token(SonarQubeMcpServerTestHarness harness) {
    stubOrganizationsList(harness, """
      [{"id":"id-1","key":"%s","name":"Auto Org","uuidV4":"%s"}]
      """.formatted(AUTO_ORG_KEY, AUTO_ORG_UUID));
    stubOrgScopedProbes(harness);

    var mcpClient = harness.newClient(CLOUD_WITHOUT_ORG);

    assertThat(mcpClient.listTools()).isNotEmpty();
    // Project search proves the auto-detected organization is retained as the session default
    mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&organization=" + AUTO_ORG_KEY)).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organizationKey=")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_fail_startup_when_listing_organizations_fails(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(() -> harness.newClient(CLOUD_WITHOUT_ORG))
      .hasMessageContaining("Failed to list SonarQube Cloud organizations for the provided token")
      .hasMessageContaining("Verify the token is valid, or set SONARQUBE_ORG explicitly");
  }

  @SonarQubeMcpServerTest
  void it_should_start_unresolved_when_token_has_no_organization(SonarQubeMcpServerTestHarness harness) {
    stubOrganizationsList(harness, "[]");

    var client = harness.newClient(CLOUD_WITHOUT_ORG);
    assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(ListOrganizationsTool.TOOL_NAME));
    assertThat(client.callTool(SearchMyProjectsTool.TOOL_NAME).content().getFirst().toString()).contains("list_organizations", "organization");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ComponentsApi.COMPONENTS_SEARCH_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_start_unresolved_when_token_has_multiple_organizations(SonarQubeMcpServerTestHarness harness) {
    stubOrganizationsList(harness, """
      [
        {"id":"id-1","key":"org-one","name":"Org One","uuidV4":"%s"},
        {"id":"id-2","key":"org-two","name":"Org Two","uuidV4":"00000000-0000-0000-0000-000000000002"}
      ]
      """.formatted(AUTO_ORG_UUID));

    var client = harness.newClient(CLOUD_WITHOUT_ORG);
    assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(ListOrganizationsTool.TOOL_NAME));
    assertThat(client.callTool(SearchMyProjectsTool.TOOL_NAME).content().getFirst().toString()).contains("list_organizations");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ComponentsApi.COMPONENTS_SEARCH_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_start_with_token_only_for_zero_one_and_multiple_organizations(SonarQubeMcpServerTestHarness harness) {
    for (var organizations : java.util.List.of("[]", "[{\"key\":\"only-org\"}]", "[{\"key\":\"org-a\"},{\"key\":\"org-b\"}]")) {
      stubOrganizationsList(harness, organizations);
      var client = harness.newClient(Map.of(
        "SONARQUBE_URL", "${SONARQUBE_URL}",
        "SONARQUBE_CLOUD_API_URL", harness.getMockSonarQubeServer().baseUrl(),
        "SONARQUBE_TOOLSETS", "issues"));
      assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(ListOrganizationsTool.TOOL_NAME));
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH + "?excludeEligibility=true")).isEqualTo(3);
  }

  private static void stubOrganizationsList(SonarQubeMcpServerTestHarness harness, String jsonBody) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(okJson(jsonBody)));
  }

  private static void stubOrgScopedProbes(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ScaApi.FEATURE_ENABLED_PATH))
      .willReturn(okJson("""
        {"enabled":false}
        """)));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + AUTO_ORG_UUID))
      .willReturn(okJson("""
        {"id":"%s","enabled":false,"eligible":true}
        """.formatted(AUTO_ORG_UUID))));
  }

}
