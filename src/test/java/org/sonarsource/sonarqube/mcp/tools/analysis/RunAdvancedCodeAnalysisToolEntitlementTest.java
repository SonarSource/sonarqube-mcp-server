/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunAdvancedCodeAnalysisToolEntitlementTest {

  private static final String ORG_KEY = "my-org";
  private static final String ORG_UUID = "57f08a8b-4a6e-4c64-bf72-83a892472f22";

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private ServerApi serverApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(wireMock.baseUrl(), ORG_KEY, null, true), httpClient);
    serverApi = new ServerApi(helper, true);
  }

  // --- isA3sEnabled (static helper) ---

  @Test
  void is_a3s_enabled_returns_true_when_org_is_enabled() {
    stubOrg(ORG_UUID);
    stubOrgConfig(ORG_UUID, true);

    assertThat(RunAdvancedCodeAnalysisTool.isA3sEnabled(serverApi, ORG_KEY)).isTrue();
  }

  @Test
  void is_a3s_enabled_returns_false_when_org_is_not_enabled() {
    stubOrg(ORG_UUID);
    stubOrgConfig(ORG_UUID, false);

    assertThat(RunAdvancedCodeAnalysisTool.isA3sEnabled(serverApi, ORG_KEY)).isFalse();
  }

  @Test
  void is_a3s_enabled_returns_false_when_org_config_endpoint_fails() {
    stubOrg(ORG_UUID);
    wireMock.stubFor(get(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(aResponse().withStatus(500)));

    assertThat(RunAdvancedCodeAnalysisTool.isA3sEnabled(serverApi, ORG_KEY)).isFalse();
  }

  // --- isEnabledFor (instance method) ---

  @Test
  void is_enabled_for_returns_true_when_no_factory_configured() {
    // No factory = tool was registered at startup after a successful A3S check, always enabled.
    var tool = new RunAdvancedCodeAnalysisTool(mock(org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider.class), null, null);
    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, ORG_KEY
    ));

    assertThat(tool.isEnabledFor(context)).isTrue();
  }

  @Test
  void is_enabled_for_returns_false_when_token_missing_from_context() {
    var tool = toolWithFactory((token, org) -> serverApi);
    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_ORG_KEY, ORG_KEY));

    assertThat(tool.isEnabledFor(context)).isFalse();
  }

  @Test
  void is_enabled_for_returns_false_when_org_missing_from_context_and_no_startup_org() {
    var tool = toolWithFactory((token, org) -> serverApi);
    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token"));

    assertThat(tool.isEnabledFor(context)).isFalse();
  }

  @Test
  void is_enabled_for_returns_true_when_org_is_enabled() {
    stubOrg(ORG_UUID);
    stubOrgConfig(ORG_UUID, true);
    var tool = toolWithFactory((token, org) -> serverApi);
    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, ORG_KEY
    ));

    assertThat(tool.isEnabledFor(context)).isTrue();
  }

  @Test
  void is_enabled_for_returns_false_when_org_is_not_enabled() {
    stubOrg(ORG_UUID);
    stubOrgConfig(ORG_UUID, false);
    var tool = toolWithFactory((token, org) -> serverApi);
    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, ORG_KEY
    ));

    assertThat(tool.isEnabledFor(context)).isFalse();
  }

  @Test
  void is_enabled_for_returns_false_when_factory_throws() {
    var tool = toolWithFactory((token, org) -> {
      throw new RuntimeException("network error");
    });
    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, ORG_KEY
    ));

    assertThat(tool.isEnabledFor(context)).isFalse();
  }

  private static RunAdvancedCodeAnalysisTool toolWithFactory(BiFunction<String, String, ServerApi> factory) {
    return new RunAdvancedCodeAnalysisTool(mock(org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider.class), factory, null, null);
  }

  private void stubOrg(String uuidV4) {
    wireMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("""
        [{"id":"old-id","uuidV4":"%s"}]
        """.formatted(uuidV4), 200)));
  }

  private void stubOrgConfig(String uuidV4, boolean enabled) {
    wireMock.stubFor(get(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + uuidV4))
      .willReturn(jsonResponse("""
        {"id":"%s","enabled":%b,"eligible":true}
        """.formatted(uuidV4, enabled), 200)));
  }

}
