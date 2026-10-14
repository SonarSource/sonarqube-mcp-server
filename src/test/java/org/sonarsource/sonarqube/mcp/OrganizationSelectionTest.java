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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class OrganizationSelectionTest {

  private record Invocation(String name, String path, Map<String, Object> arguments, String response) {
  }

  private static final Map<String, String> CLOUD_ENV = Map.of("SONARQUBE_IS_CLOUD", "true",
    "SONARQUBE_TOOLSETS", "issues,rules,quality-gates,webhooks,dependency-risks");
  private static final List<Invocation> INVOCATIONS = List.of(
    new Invocation("search_my_sonarqube_projects", "/api/components/search", Map.of(),
      "{\"components\":[],\"paging\":{\"pageIndex\":1,\"pageSize\":500,\"total\":0}}"),
    new Invocation("search_sonar_issues_in_projects", "/api/issues/search", Map.of(),
      "{\"issues\":[],\"paging\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}"),
    new Invocation("list_quality_gates", "/api/qualitygates/list", Map.of(), "{\"qualitygates\":[]}"),
    new Invocation("show_rule", "/api/rules/show", Map.of("key", "java:S1"), "{\"rule\":{\"key\":\"java:S1\"}}"),
    new Invocation("create_webhook", "/api/webhooks/create", Map.of("name", "hook", "url", "https://example.org/hook"),
      "{\"webhook\":{\"key\":\"hook\",\"name\":\"hook\",\"url\":\"https://example.org/hook\",\"hasSecret\":false}}"),
    new Invocation("list_webhooks", "/api/webhooks/list", Map.of(), "{\"webhooks\":[]}")
  );

  @SonarQubeMcpServerTest
  void it_should_scope_each_cloud_tool_without_changing_stdio_default(SonarQubeMcpServerTestHarness harness) {
    stubDefaultOrganization(harness);
    var client = harness.newClient(CLOUD_ENV);
    for (var invocation : INVOCATIONS) {
      stubInvocation(harness, invocation, "selected-org");
      stubInvocation(harness, invocation, "default-org");
      var selectedArguments = new HashMap<>(invocation.arguments());
      selectedArguments.put("organization", "selected-org");
      var selectedRequests = harness.getMockSonarQubeServer().countRequestsContaining("organization=selected-org");
      var defaultRequests = harness.getMockSonarQubeServer().countRequestsContaining("organization=default-org");
      assertThat(client.callTool(invocation.name(), selectedArguments).isError()).as(invocation.name()).isFalse();
      assertThat(client.callTool(invocation.name(), invocation.arguments()).isError()).as(invocation.name()).isFalse();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organization=selected-org") - selectedRequests).as(invocation.name() + " selected organization").isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organization=default-org") - defaultRequests).as(invocation.name() + " default organization").isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(invocation.path())).as(invocation.name()).isEqualTo(2);
    }
    assertOrganizationSchemas(client, true);
  }

  @SonarQubeMcpServerTest
  void it_should_exclude_organization_arguments_on_server(SonarQubeMcpServerTestHarness harness) {
    assertOrganizationSchemas(harness.newClient(Map.of("SONARQUBE_TOOLSETS", CLOUD_ENV.get("SONARQUBE_TOOLSETS"))), false);
  }

  @SonarQubeMcpServerTest
  void it_should_guide_discovery_before_any_organization_scoped_request(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(okJson("[]")));
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", CLOUD_ENV.get("SONARQUBE_TOOLSETS")));
    for (var invocation : INVOCATIONS) {
      var result = client.callTool(invocation.name(), invocation.arguments());
      assertThat(result.isError()).as(invocation.name()).isTrue();
      assertThat(result.toString()).contains("list_organizations", "organization");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(invocation.path())).isZero();
    }
    var risks = client.callTool("search_dependency_risks", Map.of("projectKey", "project"));
    assertThat(risks.toString()).contains("list_organizations");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ScaApi.FEATURE_ENABLED_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_default_after_failed_override(SonarQubeMcpServerTestHarness harness) {
    stubDefaultOrganization(harness);
    var invocation = INVOCATIONS.getFirst();
    stubInvocation(harness, invocation, "default-org");
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(invocation.path())).withQueryParam("organization", equalTo("forbidden-org"))
      .willReturn(aResponse().withStatus(403)));
    var client = harness.newClient(CLOUD_ENV);
    assertThat(client.callTool(invocation.name(), Map.of("organization", "forbidden-org")).isError()).isTrue();
    assertThat(client.callTool(invocation.name()).isError()).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_check_sca_entitlement_for_selected_organization(SonarQubeMcpServerTestHarness harness) {
    stubDefaultOrganization(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ScaApi.FEATURE_ENABLED_PATH))
      .withQueryParam("organization", equalTo("selected-org")).willReturn(okJson("{\"enabled\":true}")));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ScaApi.FEATURE_ENABLED_PATH))
      .withQueryParam("organization", equalTo("default-org")).willReturn(okJson("{\"enabled\":false}")));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ScaApi.DEPENDENCY_RISKS_PATH))
      .willReturn(okJson("{\"issuesReleases\":[],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}")));
    var client = harness.newClient(CLOUD_ENV);
    assertThat(client.listTools()).anyMatch(tool -> tool.name().equals("search_dependency_risks"));
    assertThat(client.callTool("search_dependency_risks", Map.of("projectKey", "project", "organization", "selected-org")).isError()).isFalse();
    assertThat(client.callTool("search_dependency_risks", Map.of("projectKey", "project")).isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ScaApi.DEPENDENCY_RISKS_PATH)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_fix_configured_organization_and_reject_all_organization_arguments(SonarQubeMcpServerTestHarness harness) {
    var environment = new HashMap<>(CLOUD_ENV);
    environment.put("SONARQUBE_ORG", "default-org");
    var client = harness.newClient(environment);
    assertOrganizationSchemas(client, false);
    for (var invocation : INVOCATIONS) {
      stubInvocation(harness, invocation, "default-org");
      for (var organization : List.of("default-org", "selected-org", "")) {
        var arguments = new HashMap<>(invocation.arguments());
        arguments.put("organization", organization);
        var result = client.callTool(invocation.name(), arguments);
        assertThat(result.isError()).as(invocation.name()).isTrue();
        assertThat(result.toString()).contains("organization", "not defined in the schema");
      }
      var arguments = new HashMap<>(invocation.arguments());
      arguments.put("organization", null);
      assertThat(client.callTool(invocation.name(), arguments).isError()).isTrue();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(invocation.path())).as(invocation.name()).isZero();
      assertThat(client.callTool(invocation.name(), invocation.arguments()).isError()).as(invocation.name()).isFalse();
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organization=selected-org")).isZero();
  }

  private static void stubDefaultOrganization(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(okJson("[{\"key\":\"default-org\",\"uuidV4\":\"default-uuid\"}]")));
  }

  private static void stubInvocation(SonarQubeMcpServerTestHarness harness, Invocation invocation, String org) {
    harness.getMockSonarQubeServer().stubFor(any(urlPathEqualTo(invocation.path())).withQueryParam("organization", equalTo(org))
      .willReturn(okJson(invocation.response())));
  }

  private static void assertOrganizationSchemas(SonarQubeMcpTestClient client, boolean cloud) {
    var names = INVOCATIONS.stream().map(Invocation::name).toList();
    client.listTools().stream().filter(tool -> names.contains(tool.name()) || tool.name().equals("search_dependency_risks")).forEach(tool -> {
      var properties = tool.inputSchema().get("properties");
      if (cloud) {
        assertThat(properties).as(tool.name()).asInstanceOf(MAP).containsKey("organization");
      } else {
        assertThat(properties).as(tool.name()).asInstanceOf(MAP).doesNotContainKey("organization");
      }
      assertThat(tool.inputSchema().get("required")).asInstanceOf(LIST).doesNotContain("organization");
    });
  }
}
