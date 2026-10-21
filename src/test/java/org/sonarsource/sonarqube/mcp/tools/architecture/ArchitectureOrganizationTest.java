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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.SMALL_JAVA_GRAPH;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.currentArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

/**
 * SonarQube Cloud without SONARQUBE_ORG: the organization comes from the tool argument.
 */
class ArchitectureOrganizationTest {

  private static final Map<String, String> CLOUD_WITHOUT_ORG = Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "architecture");

  @SonarQubeMcpServerTest
  void it_should_read_the_current_architecture_of_the_selected_organization(SonarQubeMcpServerTestHarness harness) {
    stubTwoOrganizations(harness);
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=selected-uuid&branchId=" + MAIN_BRANCH_ID
      + "&language=java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_WITHOUT_ORG);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java", "organization", "selected-org"));

    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"language\" : \"java\"");
    var tool = client.listTools().stream().filter(t -> t.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) tool.inputSchema().get("properties");
    assertThat(properties).containsKey("organization");
  }

  @SonarQubeMcpServerTest
  void it_should_ask_for_an_organization_before_reading_the_current_architecture(SonarQubeMcpServerTestHarness harness) {
    stubTwoOrganizations(harness);
    var client = harness.newClient(CLOUD_WITHOUT_ORG);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project"));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Organization is required", "list_organizations");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_read_the_intended_architecture_without_an_organization(SonarQubeMcpServerTestHarness harness) {
    stubTwoOrganizations(harness);
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID)
      .willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_WITHOUT_ORG);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));

    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"label\" : \"app\"");
  }

  @SonarQubeMcpServerTest
  void it_should_isolate_selected_organizations_in_one_session(SonarQubeMcpServerTestHarness harness) {
    stubTwoOrganizations(harness);
    stubMainBranch(harness);
    for (var organization : new String[] {"other", "selected"}) {
      harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=" + organization
        + "-uuid&branchId=" + MAIN_BRANCH_ID + "&language=java")
        .willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH.replace("app", organization)))));
    }
    var client = harness.newClient(CLOUD_WITHOUT_ORG);

    for (var organization : new String[] {"other", "selected", "other"}) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
        Map.of("target", "current", "projectKey", "my_project", "language", "java", "organization", organization + "-org"));
      assertThat(result.isError()).isFalse();
      assertThat(textOf(result)).contains("\"key\" : \"" + organization + "\"");
    }
  }

  private static void stubTwoOrganizations(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(okJson("[{\"key\":\"other-org\",\"uuidV4\":\"other-uuid\"},{\"key\":\"selected-org\",\"uuidV4\":\"selected-uuid\"}]")));
  }

}
