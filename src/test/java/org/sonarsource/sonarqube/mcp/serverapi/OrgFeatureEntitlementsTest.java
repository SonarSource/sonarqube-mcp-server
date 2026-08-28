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
package org.sonarsource.sonarqube.mcp.serverapi;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.WasFeatureFlagsApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgFeatureEntitlementsTest {

  private static final String ORG_UUID = "57f08a8b-4a6e-4c64-bf72-83a892472f22";
  private static final String ORG_KEY = "my-org";
  private static final ResolvedOrganization ORG_WITH_KNOWN_UUID = new ResolvedOrganization(ORG_KEY, ORG_UUID);
  private static final ResolvedOrganization ORG_WITHOUT_KNOWN_UUID = new ResolvedOrganization(ORG_KEY, null);

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private OrgFeatureEntitlements orgFeatureEntitlements;
  private OrgFeatureEntitlements serverOrgFeatureEntitlements;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), ORG_KEY, null, true), httpClient);
    orgFeatureEntitlements = new OrgFeatureEntitlements(new ServerApi(helper, true));
    var serverHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false), httpClient);
    serverOrgFeatureEntitlements = new OrgFeatureEntitlements(new ServerApi(serverHelper, false));
  }

  @Test
  void vortex_should_be_enabled_when_both_cag_and_a3s_are_entitled() {
    stubCagEntitlement(true);
    stubA3sConfig(true);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isTrue();
  }

  @Test
  void vortex_should_be_disabled_when_only_cag_is_entitled() {
    stubCagEntitlement(true);
    stubA3sConfig(false);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_when_cag_is_not_entitled() {
    stubCagEntitlement(false);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_when_cag_entitlement_call_fails() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID))
      .willReturn(aResponse().withStatus(500)));

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_when_a3s_config_call_fails() {
    stubCagEntitlement(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(aResponse().withStatus(500)));

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void sara_should_be_enabled_when_feature_flag_is_on() {
    stubSaraFlag(true);

    assertThat(orgFeatureEntitlements.isSaraEnabledForOrg(ORG_WITH_KNOWN_UUID)).isTrue();
  }

  @Test
  void sara_should_be_disabled_when_feature_flag_is_off() {
    stubSaraFlag(false);

    assertThat(orgFeatureEntitlements.isSaraEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void sara_should_be_disabled_when_feature_flag_call_fails() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(aResponse().withStatus(500)));

    assertThat(orgFeatureEntitlements.isSaraEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
  }

  @Test
  void it_should_resolve_org_uuid_when_not_already_known() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("""
        [{"id":"id-1","key":"%s","name":"My Org","uuidV4":"%s"}]
        """.formatted(ORG_KEY, ORG_UUID), 200)));
    stubCagEntitlement(true);
    stubA3sConfig(true);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();
  }

  @Test
  void it_should_be_disabled_when_org_uuid_cannot_be_resolved() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("[]", 200)));

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();
    assertThat(orgFeatureEntitlements.isSaraEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();
  }

  @Test
  void vortex_should_be_enabled_on_server_when_both_hubs_are_entitled() {
    stubServerCagEntitlement(true);
    stubServerA3sEntitlement(true);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isTrue();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_cag_is_not_entitled() {
    stubServerCagEntitlement(false);
    stubServerA3sEntitlement(true);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_a3s_is_not_entitled() {
    stubServerCagEntitlement(true);
    stubServerA3sEntitlement(false);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void vortex_should_be_enabled_on_server_when_cag_is_over_consumption() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverCagEntitlementPath()))
      .willReturn(jsonResponse("""
        {"allowed":false,"hasEntitlement":true}
        """, 200)));
    stubServerA3sEntitlement(true);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isTrue();
  }

  @Test
  void vortex_should_be_enabled_on_server_when_a3s_is_over_consumption() {
    stubServerCagEntitlement(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverA3sEntitlementPath()))
      .willReturn(jsonResponse("""
        {"allowed":false,"hasEntitlement":true}
        """, 200)));

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isTrue();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_cag_hub_is_absent() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverCagEntitlementPath()))
      .willReturn(aResponse().withStatus(404)));
    stubServerA3sEntitlement(true);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_a3s_hub_is_absent() {
    stubServerCagEntitlement(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverA3sEntitlementPath()))
      .willReturn(aResponse().withStatus(404)));

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_cag_hub_is_unavailable() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverCagEntitlementPath()))
      .willReturn(aResponse().withStatus(503)));
    stubServerA3sEntitlement(true);

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void vortex_should_be_disabled_on_server_when_a3s_hub_is_unavailable() {
    stubServerCagEntitlement(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverA3sEntitlementPath()))
      .willReturn(aResponse().withStatus(503)));

    assertThat(serverOrgFeatureEntitlements.isVortexEnabledForOrg(null)).isFalse();
  }

  @Test
  void sara_should_stay_disabled_on_server_without_an_organization() {
    assertThat(serverOrgFeatureEntitlements.isSaraEnabledForOrg(null)).isFalse();
  }

  private void stubCagEntitlement(boolean hasEntitlement) {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID))
      .willReturn(jsonResponse("""
        {"hasEntitlement":%s}
        """.formatted(hasEntitlement), 200)));
  }

  private void stubServerCagEntitlement(boolean hasEntitlement) {
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverCagEntitlementPath()))
      .willReturn(jsonResponse("""
        {"hasEntitlement":%s}
        """.formatted(hasEntitlement), 200)));
  }

  private void stubServerA3sEntitlement(boolean hasEntitlement) {
    sonarqubeMock.stubFor(get(urlPathEqualTo(serverA3sEntitlementPath()))
      .willReturn(jsonResponse("""
        {"hasEntitlement":%s}
        """.formatted(hasEntitlement), 200)));
  }

  private static String serverCagEntitlementPath() {
    return "/api/v2" + CagApi.CAG_ENTITLEMENT_PATH + CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER;
  }

  private static String serverA3sEntitlementPath() {
    return "/api/v2" + A3sAnalysisApi.A3S_ORG_ENTITLEMENT_PATH + CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER;
  }

  private void stubA3sConfig(boolean enabled) {
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(jsonResponse("""
        {"id":"%s","enabled":%s,"eligible":true}
        """.formatted(ORG_UUID, enabled), 200)));
  }

  private void stubSaraFlag(boolean enabled) {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(jsonResponse("""
        {"%s":%s}
        """.formatted(WasFeatureFlagsApi.SARA_FEATURE_FLAG_KEY, enabled), 200)));
  }
}
