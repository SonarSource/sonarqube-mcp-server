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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.WebhooksApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HostedOAuthScopeInventoryTest {

  @SonarQubeMcpServerTest
  void it_should_require_an_explicit_scope_audit_for_every_hosted_tool(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) {
    var server = new SonarQubeMcpServer(Map.of(
      "STORAGE_PATH", storage.toString(), "SONARQUBE_TRANSPORT", "http", "SONARQUBE_IS_CLOUD", "true",
      "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "TELEMETRY_DISABLED", "true",
      "SONARQUBE_TOOLSETS", ToolCategory.all().stream().map(ToolCategory::getKey).collect(Collectors.joining(","))));
    try {
      var expected = new HashMap<String, Set<String>>();
      for (var name : List.of("list_enterprises", "list_organizations", "search_my_sonarqube_projects", "search_sonar_issues_in_projects",
        "search_security_hotspots", "show_security_hotspot", "get_project_quality_gate_status", "show_rule", "list_quality_gates",
        "list_languages", "get_component_measures", "search_files_by_coverage", "get_file_coverage_details", "search_metrics", "get_scm_info",
        "get_raw_source", "list_webhooks", "get_duplications", "search_duplicated_files", "list_portfolios", "list_pull_requests", "list_branches",
        "search_dependency_risks", "get_agentic_readiness_assessment", "list_agentic_readiness_assessments")) {
        expected.put(name, Set.of("read:all"));
      }
      for (var name : List.of("change_sonar_issue_status", "change_security_hotspot_status", "create_webhook")) {
        expected.put(name, Set.of("write:all"));
      }
      expected.put("start_agentic_readiness_assessment", Set.of("read:all", "write:all"));

      var actual = server.getSupportedTools().stream().collect(Collectors.toMap(tool -> tool.definition().name(), Tool::requiredOAuthScopes));
      assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_mutation_tool_requests_including_optional_paths(SonarQubeMcpServerTestHarness harness) {
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(post(IssuesApi.DO_TRANSITION_PATH).willReturn(ok()));
    backend.stubFor(post(IssuesApi.ADD_COMMENT_PATH).willReturn(ok()));
    backend.stubFor(post(HotspotsApi.CHANGE_STATUS_PATH).willReturn(ok()));
    backend.stubFor(post(urlPathEqualTo(WebhooksApi.CREATE_PATH)).willReturn(okJson("""
      {"webhook":{"key":"webhook","name":"hook","url":"https://example.com/hook","hasSecret":false}}
      """)));
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "issues,security-hotspots,webhooks"));

    assertRequestCount(harness, client, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"), 1);
    assertRequestCount(harness, client, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept", "comment", "reviewed"), 2);
    assertRequestCount(harness, client, "change_security_hotspot_status", Map.of("hotspotKey", "hotspot", "status", "TO_REVIEW"), 1);
    assertRequestCount(harness, client, "change_security_hotspot_status",
      Map.of("hotspotKey", "hotspot", "status", "REVIEWED", "resolution", "SAFE", "comment", "reviewed"), 1);
    assertRequestCount(harness, client, "create_webhook", Map.of("name", "hook", "url", "https://example.com/hook"), 1);
    assertRequestCount(harness, client, "create_webhook", Map.of("name", "hook", "url", "https://example.com/hook", "projectKey", "project"), 1);
    assertThat(backend.countRequestsContaining(IssuesApi.DO_TRANSITION_PATH)).isEqualTo(2);
    assertThat(backend.countRequestsContaining(IssuesApi.ADD_COMMENT_PATH)).isEqualTo(1);
    assertThat(backend.countRequestsContaining(HotspotsApi.CHANGE_STATUS_PATH)).isEqualTo(2);
    assertThat(backend.countRequestsContaining(WebhooksApi.CREATE_PATH)).isEqualTo(2);
  }

  @SonarQubeMcpServerTest
  void it_should_resolve_the_project_before_starting_the_assessment(SonarQubeMcpServerTestHarness harness) {
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(urlPathEqualTo(ProjectBranchesApi.BRANCHES_LIST_PATH)).willReturn(okJson("""
      {"branches":[{"name":"main","isMain":true,"branchId":"project-id"}]}
      """)));
    backend.stubFor(post(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH)).willReturn(okJson("""
      {"id":"assessment-id","status":"PENDING","createdAt":"2026-10-15T12:00:00Z"}
      """)));
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "agentic-readiness"));

    assertRequestCount(harness, client, "start_agentic_readiness_assessment", Map.of("projectKey", "project"), 2);
    assertThat(backend.countRequestsContaining(ProjectBranchesApi.BRANCHES_LIST_PATH)).isEqualTo(1);
    assertThat(backend.countRequestsContaining(AgenticReadinessApi.ASSESSMENTS_PATH)).isEqualTo(1);
  }

  private static void assertRequestCount(SonarQubeMcpServerTestHarness harness, SonarQubeMcpTestClient client,
    String tool, Map<String, Object> arguments, int expectedRequests) {
    var before = harness.getMockSonarQubeServer().getReceivedRequests().size();
    assertThat(client.callTool(tool, arguments).isError()).isFalse();
    // Only the audited methods above are stubbed; extra reads or writes also fail this count.
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).hasSize(before + expectedRequests);
  }
}
