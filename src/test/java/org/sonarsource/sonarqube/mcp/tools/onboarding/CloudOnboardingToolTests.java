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
package org.sonarsource.sonarqube.mcp.tools.onboarding;

import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class CloudOnboardingToolTests {
  private static final String ORG = """
    {"key":"org","name":"Org","actions":{"admin":true},"alm":{"key":"github","url":"https://github.com/owner"}}
    """;
  private static final String TRIAL = """
    {"planKey":"team","status":"active","trial":true,"trialPeriod":{"start":"2020-01-01T00:00:00Z","end":"2099-01-01T00:00:00Z"}}
    """;

  private static SonarQubeMcpTestClient client(SonarQubeMcpServerTestHarness harness) {
    environment(harness);
    return harness.newClient(Map.of("SONARQUBE_ORG", "org", "SONARQUBE_CLOUD_API_URL", harness.getMockSonarQubeServer().baseUrl()));
  }

  private static void verify(SonarQubeMcpServerTestHarness harness, int count, com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern) {
    new com.github.tomakehurst.wiremock.client.WireMock("localhost", harness.getMockSonarQubeServer().getPort()).verifyThat(count, pattern);
  }

  private static void verify(SonarQubeMcpServerTestHarness harness, com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern) {
    verify(harness, 1, pattern);
  }

  private static String json(McpSchema.CallToolResult result) {
    assertThat(result.isError()).as(result.content().toString()).isFalse();
    return ((McpSchema.TextContent) result.content().getFirst()).text();
  }

  private static void getJson(SonarQubeMcpServerTestHarness harness, String path, String body) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).atPriority(path.equals("/api/users/current") ? 1 : 5).willReturn(okJson(body)));
  }

  private static void environment(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/static_configuration/configuration.json", "{\"environmentName\":\"dev9\",\"api\":{\"v2\":\"" + harness.getMockSonarQubeServer().baseUrl() + "\"}}");
  }

  private static void emptyMembers(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/organizations/search", "{\"organizations\":[],\"paging\":{\"total\":0}}");
  }

  private static void organization(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/organizations/search", "{\"organizations\":[" + ORG + "],\"paging\":{\"total\":1}}");
  }

  private static void subscriptionSetup(SonarQubeMcpServerTestHarness harness) {
    organization(harness);
    getJson(harness, "/organizations/organizations", "[{\"key\":\"org\",\"uuidV4\":\"uuid\"}]");
    getJson(harness, "/api/users/current", "{\"email\":\"user@example.com\"}");
    getJson(harness, "/billing/customers", "{\"paymentMethodStatus\":\"NONE\"}");
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/billing/subscriptions")).willReturn(okJson("{}")));
  }

  @SonarQubeMcpServerTest
  void it_should_onboard_and_review_a_project_over_http_without_an_organization_header(SonarQubeMcpServerTestHarness harness) throws Exception {
    emptyMembers(harness);
    environment(harness);
    getJson(harness, "/api/project_branches/list", "{\"branches\":[{\"name\":\"main\",\"isMain\":true,\"type\":\"LONG\"}]}");
    getJson(harness, "/api/qualitygates/project_status", "{\"projectStatus\":{\"status\":\"OK\",\"conditions\":[]}}");
    getJson(harness, "/api/issues/search", "{\"issues\":[],\"paging\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}");
    getJson(harness, "/api/hotspots/search", "{\"hotspots\":[],\"paging\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0},\"components\":[]}");
    int port;
    try (var socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var storage = java.nio.file.Files.createTempDirectory("mcp-onboarding-http-test");
    var server = new org.sonarsource.sonarqube.mcp.SonarQubeMcpServer(Map.of(
      "SONARQUBE_TRANSPORT", "http", "SONARQUBE_HTTP_PORT", String.valueOf(port), "STORAGE_PATH", storage.toString(),
      "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "SONARQUBE_IS_CLOUD", "true",
      "SONARQUBE_TOOLSETS", "projects,issues,quality-gates,security-hotspots", "TELEMETRY_DISABLED", "true"));
    try (var http = java.net.http.HttpClient.newHttpClient()) {
      server.start();
      for (var call : java.util.List.of(
        "{\"name\":\"discover_github_repository\",\"arguments\":{\"repository\":\"owner/repo\"}}",
        "{\"name\":\"list_branches\",\"arguments\":{\"projectKey\":\"project\"}}",
        "{\"name\":\"get_project_quality_gate_status\",\"arguments\":{\"projectKey\":\"project\"}}",
        "{\"name\":\"search_sonar_issues_in_projects\",\"arguments\":{\"projectKeys\":[\"project\"]}}",
        "{\"name\":\"search_security_hotspots\",\"arguments\":{\"projectKey\":\"project\"}}")) {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/mcp"))
          .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
          .header("Authorization", "Bearer token").header("MCP-Protocol-Version", "2025-11-25")
          .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":" + call + "}"))
          .build();
        var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("\"isError\":false");
      }
    } finally {
      server.shutdown();
      org.apache.commons.io.FileUtils.deleteDirectory(storage.toFile());
    }
  }

  @SonarQubeMcpServerTest
  void it_should_reject_mismatched_environment_configuration(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    var mcp = client(harness);
    getJson(harness, "/static_configuration/configuration.json", "{\"environmentName\":\"production\",\"api\":{\"v2\":\"https://api.sonarcloud.io\"}}");
    var result = mcp.callTool(DiscoverGitHubRepositoryTool.TOOL_NAME, Map.of("repository", "owner/repo"));
    assertThat(result.isError()).isTrue();
    assertThat(result.content().toString()).contains("does not match");
    verify(harness, 0, postRequestedFor(anyUrl()));
  }

  @SonarQubeMcpServerTest
  void it_should_register_cloud_only_tools_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var tools = client(harness).listTools().stream().filter(t -> t.name().matches("discover_github_repository|import_github_organization|ensure_cloud_subscription|import_github_repository|get_cloud_analysis_status")).toList();
    assertThat(tools).hasSize(5);
    for (var tool : tools) {
      assertThat(tool.annotations().openWorldHint()).isTrue();
      assertThat(tool.annotations().readOnlyHint()).isEqualTo(tool.name().equals(DiscoverGitHubRepositoryTool.TOOL_NAME));
      assertThat(tool.outputSchema()).isNull();
    }
    assertThat(harness.newClient().listTools()).noneMatch(t -> t.name().equals(ImportGitHubOrganizationTool.TOOL_NAME));
  }

  @SonarQubeMcpServerTest
  void it_should_hide_mutations_in_read_only_mode(SonarQubeMcpServerTestHarness harness) {
    var tools = harness.newClient(Map.of("SONARQUBE_ORG", "org", "SONARQUBE_READ_ONLY", "true")).listTools();
    assertThat(tools).anyMatch(t -> t.name().equals(DiscoverGitHubRepositoryTool.TOOL_NAME));
    assertThat(tools).noneMatch(t -> t.name().equals(EnsureCloudSubscriptionTool.TOOL_NAME) || t.name().equals(GetCloudAnalysisStatusTool.TOOL_NAME));
  }

  @SonarQubeMcpServerTest
  void it_should_discover_exact_repository_bindings_across_pages(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/organizations/search")).withQueryParam("p", equalTo("1"))
      .willReturn(okJson("{\"organizations\":[],\"paging\":{\"total\":101}}")));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/organizations/search")).withQueryParam("p", equalTo("2"))
      .willReturn(okJson("{\"organizations\":[" + ORG + "],\"paging\":{\"total\":101}}")));
    getJson(harness, "/api/alm_integration/list_repositories", """
      {"repositories":[{"installationKey":"owner/repo|1","linkedProjects":[{"key":"project","name":"Project"}]},
      {"slug":"owner/repo-other","installationKey":"owner/repo-other|2","linkedProjects":[]}]}
      """);
    var result = json(client(harness).callTool(DiscoverGitHubRepositoryTool.TOOL_NAME, Map.of("repository", "OWNER/REPO")));
    assertThat(result).contains("project", "org").doesNotContain("repo-other");
    assertThat(JsonParser.parseString(result).getAsJsonObject().get("server").getAsString()).isEqualTo(harness.getMockSonarQubeServer().baseUrl());
  }

  @SonarQubeMcpServerTest
  void it_should_return_empty_discovery_without_provisioning(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    var result = json(client(harness).callTool(DiscoverGitHubRepositoryTool.TOOL_NAME, Map.of("repository", "owner/repo")));
    assertThat(JsonParser.parseString(result).getAsJsonObject().getAsJsonArray("organizations")).isEmpty();
    verify(harness, 0, postRequestedFor(anyUrl()));
  }

  @SonarQubeMcpServerTest
  void it_should_return_browser_handoff_when_installation_is_missing(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    getJson(harness, "/api/alm_integration/list_unbound_applications", "{\"applications\":[]}");
    getJson(harness, "/api/alm_integration/show_app_info", "{\"application\":{\"installationUrl\":\"https://github.com/apps/dev9/installations/new\"}}");
    assertThat(json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner"))))
      .contains("browser_required", "https://github.com/apps/dev9/installations/new?state=sonarqube-mcp", "user");
    verify(harness, 0, postRequestedFor(anyUrl()));
  }

  @SonarQubeMcpServerTest
  void it_should_replace_installation_state_and_preserve_other_url_parameters(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    getJson(harness, "/api/alm_integration/list_unbound_applications", "{\"applications\":[]}");
    getJson(harness, "/api/alm_integration/show_app_info", """
      {"application":{"installationUrl":"https://github.com/apps/dev9/installations/new?state=previous&target_id=123#access"}}
      """);
    var response = JsonParser.parseString(json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner"))))
      .getAsJsonObject();
    assertThat(response.get("url").getAsString())
      .isEqualTo("https://github.com/apps/dev9/installations/new?target_id=123&state=sonarqube-mcp#access");
    verify(harness, 0, postRequestedFor(anyUrl()));
  }

  @SonarQubeMcpServerTest
  void it_should_reject_untrusted_installation_url(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    getJson(harness, "/api/alm_integration/list_unbound_applications", "{\"applications\":[]}");
    getJson(harness, "/api/alm_integration/show_app_info", "{\"application\":{\"installationUrl\":\"https://evil.example/new\"}}");
    var result = client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner"));
    assertThat(result.isError()).isTrue();
    assertThat(result.content().toString()).contains("unexpected GitHub installation URL");
  }

  @SonarQubeMcpServerTest
  void it_should_reuse_bound_organization_without_billing_or_installation(SonarQubeMcpServerTestHarness harness) {
    organization(harness);
    assertThat(json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner"))))
      .contains("ready", "org");
    verify(harness, 0, getRequestedFor(urlPathEqualTo("/billing/subscriptions")));
    verify(harness, 0, getRequestedFor(urlPathEqualTo("/api/alm_integration/list_unbound_applications")));
    verify(harness, 0, postRequestedFor(anyUrl()));
  }

  @SonarQubeMcpServerTest
  void it_should_create_and_bind_the_verified_installation(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/show_dop_organization"))
      .willReturn(okJson("{\"almOrganization\":{\"key\":\"owner\",\"name\":\"Owner\"}}")));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/organizations/create"))
      .withRequestBody(containing("installationId=123"))
      .willReturn(okJson("{\"organization\":{\"key\":\"owner-custom\",\"name\":\"Owner\"}}")));
    var result = json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner", "installationId", "123", "organizationKey", "owner-custom")));
    assertThat(result).contains("owner-custom", "ready");
    verify(harness, postRequestedFor(urlPathEqualTo("/api/organizations/create")).withRequestBody(containing("key=owner-custom")));
  }

  @SonarQubeMcpServerTest
  void it_should_bind_unbound_organization_and_preserve_its_subscription(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/organizations/search")).withQueryParam("organizations", equalTo("org"))
      .willReturn(okJson("{\"organizations\":[{\"key\":\"org\",\"name\":\"Org\",\"actions\":{\"admin\":true}}]}")));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/show_dop_organization"))
      .willReturn(okJson("{\"almOrganization\":{\"key\":\"owner\",\"name\":\"Owner\"}}")));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/bind_organization")).willReturn(ok()));
    assertThat(json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner", "installationId", "123", "organizationKey", "org")))).contains("ready");
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/api/organizations/create")));
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
  }

  @SonarQubeMcpServerTest
  void it_should_reject_wrong_installation_and_key_collision(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/show_dop_organization"))
      .willReturn(okJson("{\"almOrganization\":{\"key\":\"other\",\"name\":\"Other\"}}")));
    var mcp = client(harness);
    assertThat(mcp.callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner", "installationId", "123")).isError()).isTrue();
    organization(harness);
    assertThat(mcp.callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner", "organizationKey", "other")).isError()).isTrue();
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/api/organizations/create")));
  }

  @SonarQubeMcpServerTest
  void it_should_resume_pending_installation(SonarQubeMcpServerTestHarness harness) {
    emptyMembers(harness);
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/show_dop_organization")).willReturn(notFound()));
    assertThat(json(client(harness).callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner", "installationId", "123")))).contains("pending");
  }

  @SonarQubeMcpServerTest
  void it_should_start_and_verify_cardless_team_trial(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("trial").whenScenarioStateIs("Started")
      .willReturn(okJson("{\"subscriptions\":[]}")).willSetStateTo("created"));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("trial").whenScenarioStateIs("created")
      .willReturn(okJson("{\"subscriptions\":[" + TRIAL + "]}")));
    assertThat(json(client(harness).callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org"))))
      .contains("ready", "team", "NONE");
    verify(harness, postRequestedFor(urlPathEqualTo("/billing/subscriptions"))
      .withRequestBody(matchingJsonPath("$.entityId", equalTo("uuid")))
      .withRequestBody(matchingJsonPath("$.email", equalTo("user@example.com")))
      .withRequestBody(notMatching(".*priceId.*")));
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_existing_free_subscription(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/subscriptions", "{\"subscriptions\":[{\"planKey\":\"free_v2\",\"trial\":false}]}");
    assertThat(json(client(harness).callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org")))).contains("reused", "free_v2");
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
  }

  @SonarQubeMcpServerTest
  void it_should_not_submit_signup_again_while_pending(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/subscriptions", "{\"subscriptions\":[]}");
    var mcp = client(harness);
    assertThat(json(mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org")))).contains("pending");
    assertThat(json(mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org", "createIfMissing", false)))).contains("pending");
    verify(harness, 1, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
  }

  @SonarQubeMcpServerTest
  void it_should_verify_resumed_trial_without_creating_another_subscription(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/subscriptions", "{\"subscriptions\":[" + TRIAL + "]}");
    var mcp = client(harness);
    assertThat(json(mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org", "createIfMissing", false))))
      .contains("ready", "team", "NONE");
    getJson(harness, "/billing/customers", "{\"paymentMethodStatus\":\"VALID\"}");
    var invalid = mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org", "createIfMissing", false));
    assertThat(invalid.isError()).isTrue();
    assertThat(invalid.content().toString()).contains("cardless trial");
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
  }

  @SonarQubeMcpServerTest
  void it_should_verify_resumed_free_signup_and_reject_unexpected_plan(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/subscriptions", "{\"subscriptions\":[{\"planKey\":\"free_v2\",\"trial\":false}]}");
    var mcp = client(harness);
    var arguments = Map.<String, Object>of("organizationKey", "org", "plan", "free", "createIfMissing", false);
    assertThat(json(mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, arguments))).contains("ready", "free_v2");
    getJson(harness, "/billing/subscriptions", "{\"subscriptions\":[" + TRIAL + "]}");
    var invalid = mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, arguments);
    assertThat(invalid.isError()).isTrue();
    assertThat(invalid.content().toString()).contains("requested Free subscription");
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
  }

  @SonarQubeMcpServerTest
  void it_should_normalize_dashboard_url_with_trailing_server_slash(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/project_analyses/search", "{\"analyses\":[{\"key\":\"analysis\",\"date\":\"2026-01-01\"}]}");
    var mcp = harness.newClient(Map.of("SONARQUBE_ORG", "org", "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl() + "/"));
    var result = JsonParser.parseString(json(mcp.callTool(GetCloudAnalysisStatusTool.TOOL_NAME, Map.of("projectKey", "p")))).getAsJsonObject();
    assertThat(result.get("url").getAsString()).isEqualTo(harness.getMockSonarQubeServer().baseUrl() + "/dashboard?id=p");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_unverified_cardless_trial(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/customers", "{\"paymentMethodStatus\":\"VALID\"}");
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("invalid").whenScenarioStateIs("Started")
      .willReturn(okJson("{\"subscriptions\":[]}")).willSetStateTo("created"));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("invalid").whenScenarioStateIs("created")
      .willReturn(okJson("{\"subscriptions\":[" + TRIAL + "]}")));
    var result = client(harness).callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org"));
    assertThat(result.isError()).isTrue();
    assertThat(result.content().toString()).contains("cardless trial");
  }

  @SonarQubeMcpServerTest
  void it_should_only_select_zero_cost_free_price(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    getJson(harness, "/billing/plans", """
      [{"name":"free_v2","tiers":[{"priceId":"zero","currencyOptions":[{"unitAmount":0}]}]},
      {"name":"team","tiers":[{"priceId":"paid","currencyOptions":[{"unitAmount":100}]}]}]
      """);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("free").whenScenarioStateIs("Started")
      .willReturn(okJson("{\"subscriptions\":[]}")).willSetStateTo("created"));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).inScenario("free").whenScenarioStateIs("created")
      .willReturn(okJson("{\"subscriptions\":[{\"planKey\":\"free_v2\",\"trial\":false}]}")));
    assertThat(json(client(harness).callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org", "plan", "free")))).contains("ready", "free_v2");
    verify(harness, postRequestedFor(urlPathEqualTo("/billing/subscriptions")).withRequestBody(matchingJsonPath("$.priceId", equalTo("zero"))));
  }

  @SonarQubeMcpServerTest
  void it_should_import_using_server_returned_installation_key_and_reuse_binding(SonarQubeMcpServerTestHarness harness) {
    organization(harness);
    getJson(harness, "/api/alm_integration/list_repositories", "{\"repositories\":[{\"slug\":\"owner/repo\",\"installationKey\":\"owner/repo|123\",\"linkedProjects\":[]}]}");
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo("/api/alm_integration/provision_projects"))
      .withRequestBody(containing("installationKeys=owner%2Frepo%7C123")).willReturn(okJson("{\"projects\":[{\"key\":\"project\",\"name\":\"Project\"}]}")));
    var mcp = client(harness);
    assertThat(json(mcp.callTool(ImportGitHubRepositoryTool.TOOL_NAME, Map.of("organizationKey", "org", "repository", "owner/repo")))).contains("ready", "project");
    getJson(harness, "/api/alm_integration/list_repositories", "{\"repositories\":[{\"slug\":\"owner/repo\",\"installationKey\":\"owner/repo|123\",\"linkedProjects\":[{\"key\":\"project\",\"name\":\"Project\"}]}]}");
    assertThat(json(mcp.callTool(ImportGitHubRepositoryTool.TOOL_NAME, Map.of("organizationKey", "org", "repository", "owner/repo")))).contains("reused");
    verify(harness, 1, postRequestedFor(urlPathEqualTo("/api/alm_integration/provision_projects")));
  }

  @SonarQubeMcpServerTest
  void it_should_reject_missing_repository_access(SonarQubeMcpServerTestHarness harness) {
    organization(harness);
    getJson(harness, "/api/alm_integration/list_repositories", "{\"repositories\":[]}");
    var result = client(harness).callTool(ImportGitHubRepositoryTool.TOOL_NAME, Map.of("organizationKey", "org", "repository", "owner/repo"));
    assertThat(result.isError()).isTrue();
    assertThat(result.content().toString()).contains("Repository access is not confirmed");
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/api/alm_integration/provision_projects")));
  }

  @SonarQubeMcpServerTest
  void it_should_reuse_completed_analysis_without_enabling_autoscan(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/project_analyses/search", "{\"analyses\":[{\"key\":\"analysis\",\"date\":\"2026-01-01\",\"revision\":\"sha\"}]}");
    assertThat(json(client(harness).callTool(GetCloudAnalysisStatusTool.TOOL_NAME, Map.of("projectKey", "p", "enableAutomaticAnalysis", true))))
      .contains("completed", "sha", "dashboard?id=p");
    verify(harness, 0, getRequestedFor(urlPathEqualTo("/api/autoscan/eligibility")));
  }

  @SonarQubeMcpServerTest
  void it_should_handle_asynchronous_eligibility_and_ineligible_projects(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/project_analyses/search", "{\"analyses\":[]}");
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/autoscan/eligibility")).withQueryParam("autoEnable", equalTo("true"))
      .willReturn(aResponse().withStatus(202)));
    var mcp = client(harness);
    assertThat(json(mcp.callTool(GetCloudAnalysisStatusTool.TOOL_NAME, Map.of("projectKey", "p", "enableAutomaticAnalysis", true)))).contains("pending");
    getJson(harness, "/api/autoscan/eligibility", "{\"eligible\":false,\"ineligibilityReason\":\"unsupported language\"}");
    assertThat(json(mcp.callTool(GetCloudAnalysisStatusTool.TOOL_NAME, Map.of("projectKey", "p")))).contains("ci_required", "unsupported language");
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_authorization_and_network_errors(SonarQubeMcpServerTestHarness harness) {
    subscriptionSetup(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/billing/subscriptions")).willReturn(aResponse().withStatus(403)));
    var mcp = client(harness);
    assertThat(mcp.callTool(EnsureCloudSubscriptionTool.TOOL_NAME, Map.of("organizationKey", "org")).isError()).isTrue();
    verify(harness, 0, postRequestedFor(urlPathEqualTo("/billing/subscriptions")));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/organizations/search")).willReturn(aResponse().withStatus(401)));
    assertThat(mcp.callTool(DiscoverGitHubRepositoryTool.TOOL_NAME, Map.of("repository", "owner/repo")).isError()).isTrue();
    assertThat(mcp.callTool(ImportGitHubOrganizationTool.TOOL_NAME, Map.of("github", "owner")).isError()).isTrue();
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/api/project_analyses/search")).willReturn(aResponse().withStatus(500)));
    assertThat(mcp.callTool(GetCloudAnalysisStatusTool.TOOL_NAME, Map.of("projectKey", "p")).isError()).isTrue();
  }
}
