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
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.WasFeatureFlagsApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.users.UsersApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
  private HttpClientProvider httpClientProvider;
  private ServerApiHelper cloudHelper;
  private ServerApiHelper serverHelper;
  private ServerApi cloudApi;

  @BeforeAll
  void initHelpers() {
    httpClientProvider = new HttpClientProvider("test");
    var httpClient = httpClientProvider.getHttpClient("token");
    cloudHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), ORG_KEY, null, true), httpClient);
    serverHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false), httpClient);
  }

  @BeforeEach
  void init() {
    cloudApi = new ServerApi(cloudHelper, true);
    orgFeatureEntitlements = cloudApi.orgFeatureEntitlements();
    serverOrgFeatureEntitlements = new ServerApi(serverHelper, false).orgFeatureEntitlements();
  }

  @AfterAll
  void closeHttpClient() {
    httpClientProvider.shutdown();
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
  void it_should_reuse_startup_entitlement_and_known_uuid_for_explicit_default_selection() {
    stubCagEntitlement(true);
    stubA3sConfig(true);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isTrue();
    var selectedApi = cloudApi.withOrganization(ORG_KEY);
    assertThat(selectedApi.orgFeatureEntitlements().isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();

    verifyLookupCounts(0, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_share_organization_uuids_between_telemetry_and_entitlements(boolean discoveredAtStartup) {
    if (discoveredAtStartup) {
      cloudApi.cacheOrganizationUuid(ORG_WITH_KNOWN_UUID);
    }
    stubOrganizationUuid();
    stubCagEntitlement(false);
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH).willReturn(jsonResponse("{\"id\":\"user-id\"}", 200)));
    var context = ConnectionContext.empty();

    context.resolveFrom(cloudApi);
    assertThat(context.getOrganizationUuidV4()).isEqualTo(ORG_UUID);
    var selectedApi = cloudApi.withOrganization(ORG_KEY);
    assertThat(selectedApi.orgFeatureEntitlements().isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();
    assertThat(context.snapshotFor(selectedApi, context.snapshot()).organizationUuidV4()).isEqualTo(ORG_UUID);

    verifyLookupCounts(discoveredAtStartup ? 0 : 1, 1, 0);
    assertThat(selectedApi.usersApi().getCurrentUserId()).isEqualTo("user-id");
    sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(UsersApi.CURRENT_USER_PATH)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_cache_confirmed_unentitled_results(boolean cagEnabled) {
    stubOrganizationUuid();
    stubCagEntitlement(cagEnabled);
    stubA3sConfig(false);

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();
    assertThat(cloudApi.withOrganization(ORG_KEY).orgFeatureEntitlements().isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();

    verifyLookupCounts(1, 1, cagEnabled ? 1 : 0);
  }

  @ParameterizedTest
  // The HTTP client retries a 503 once before the entitlement check returns a failure.
  @CsvSource({"uuid, 500, 2, 1, 1", "uuid, 200, 2, 1, 1", "cag, 503, 1, 3, 1", "a3s, 503, 1, 2, 3"})
  void it_should_retry_missing_or_failed_checks_without_refetching_resolved_uuid(String failingApi, int status,
    int uuidLookups, int cagLookups, int a3sLookups) {
    stubOrganizationUuid();
    stubCagEntitlement(true);
    stubA3sConfig(true);
    var failingPath = switch (failingApi) {
      case "uuid" -> OrganizationsApi.ORGANIZATIONS_PATH;
      case "cag" -> CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID;
      default -> A3sAnalysisApi.A3S_ORG_CONFIG_PATH + ORG_UUID;
    };
    sonarqubeMock.stubFor(get(urlPathEqualTo(failingPath)).willReturn(aResponse().withStatus(status).withBody("[]")));
    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isFalse();

    stubOrganizationUuid();
    stubCagEntitlement(true);
    stubA3sConfig(true);
    var selected = cloudApi.withOrganization(ORG_KEY).orgFeatureEntitlements();
    assertThat(selected.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();
    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();

    verifyLookupCounts(uuidLookups, cagLookups, a3sLookups);
  }

  @Test
  void it_should_cache_organizations_independently_without_changing_the_default() {
    stubOrganizationUuid();
    stubCagEntitlement(true);
    stubA3sConfig(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).withQueryParam("organizationKey", equalTo("other-org"))
      .willReturn(jsonResponse("[{\"uuidV4\":\"other-uuid\"}]", 200)));
    sonarqubeMock.stubFor(get(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + "other-uuid"))
      .willReturn(jsonResponse("{\"hasEntitlement\":false}", 200)));

    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();
    var otherOrg = ResolvedOrganization.fromKey("other-org");
    assertThat(cloudApi.withOrganization("other-org").orgFeatureEntitlements().isVortexEnabledForOrg(otherOrg)).isFalse();
    assertThat(cloudApi.withOrganization("other-org").orgFeatureEntitlements().isVortexEnabledForOrg(otherOrg)).isFalse();
    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID)).isTrue();
    assertThat(cloudApi.getOrganization()).isEqualTo(ORG_KEY);

    verifyLookupCounts(2, 1, 1);
    sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + "other-uuid")));
  }

  @Test
  void it_should_coalesce_concurrent_checks_for_the_same_organization() throws Exception {
    stubOrganizationUuid();
    stubA3sConfig(true);
    sonarqubeMock.stubFor(get(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID))
      .willReturn(jsonResponse("{\"hasEntitlement\":true}", 200).withFixedDelay(100)));

    try (var executor = Executors.newFixedThreadPool(4)) {
      Callable<Boolean> check = () -> cloudApi.withOrganization(ORG_KEY).orgFeatureEntitlements().isVortexEnabledForOrg(ORG_WITHOUT_KNOWN_UUID);
      for (var result : executor.invokeAll(Collections.nCopies(8, check), 5, TimeUnit.SECONDS)) {
        assertThat(result.get()).isTrue();
      }
    }

    verifyLookupCounts(1, 1, 1);
  }

  @Test
  void it_should_keep_fresh_http_request_apis_isolated_across_tokens() {
    stubOrganizationUuid();
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH).willReturn(jsonResponse("{\"id\":\"user-a\"}", 200)));
    assertThat(cloudApi.resolveOrganizationUuid(ORG_WITHOUT_KNOWN_UUID)).isEqualTo(ORG_UUID);
    assertThat(cloudApi.usersApi().getCurrentUserId()).isEqualTo("user-a");
    stubCagEntitlement(true);
    stubA3sConfig(true);
    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isTrue();

    stubCagEntitlement(false);
    var requestHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), ORG_KEY, null, true),
      httpClientProvider.getHttpClient("other-token"));
    var requestApi = new ServerApi(requestHelper, true).withOrganization(ORG_KEY);
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH).willReturn(jsonResponse("{\"id\":\"user-b\"}", 200)));
    assertThat(requestApi.resolveOrganizationUuid(ORG_WITHOUT_KNOWN_UUID)).isEqualTo(ORG_UUID);
    assertThat(requestApi.usersApi().getCurrentUserId()).isEqualTo("user-b");
    assertThat(cloudApi.usersApi().getCurrentUserId()).isEqualTo("user-a");
    assertThat(requestApi.orgFeatureEntitlements().isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isFalse();
    assertThat(orgFeatureEntitlements.isVortexEnabledForOrg(ORG_WITH_KNOWN_UUID)).isTrue();

    sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID))
      .withHeader("Authorization", equalTo("Bearer token")));
    sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID))
      .withHeader("Authorization", equalTo("Bearer other-token")));
    for (var token : java.util.List.of("token", "other-token")) {
      sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
        .withHeader("Authorization", equalTo("Bearer " + token)));
      sonarqubeMock.verify(1, getRequestedFor(urlPathEqualTo(UsersApi.CURRENT_USER_PATH))
        .withHeader("Authorization", equalTo("Bearer " + token)));
    }
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

  private void stubOrganizationUuid() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("[{\"uuidV4\":\"%s\"}]".formatted(ORG_UUID), 200)));
  }

  private void verifyLookupCounts(int uuidLookups, int cagLookups, int a3sLookups) {
    sonarqubeMock.verify(uuidLookups, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)));
    sonarqubeMock.verify(cagLookups, getRequestedFor(urlPathEqualTo(CagApi.CAG_ENTITLEMENT_PATH + ORG_UUID)));
    sonarqubeMock.verify(a3sLookups, getRequestedFor(urlPathEqualTo(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + ORG_UUID)));
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
