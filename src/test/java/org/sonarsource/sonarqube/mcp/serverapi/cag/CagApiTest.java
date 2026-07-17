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
package org.sonarsource.sonarqube.mcp.serverapi.cag;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CagApiTest {

  private static final String ORG_UUID = "57f08a8b-4a6e-4c64-bf72-83a892472f22";
  private static final String CAG_ENTITLEMENT_PUBLIC_PATH = "/cag/cag-entitlement/" + ORG_UUID;

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private CagApi cagApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "my-org", null, true), httpClient);
    cagApi = new CagApi(helper);
  }

  @Test
  void it_should_return_cag_entitlement_when_org_is_entitled() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(jsonResponse("""
        {"hasEntitlement":true}
        """, 200)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNotNull();
    assertThat(entitlement.hasEntitlement()).isTrue();
  }

  @Test
  void it_should_return_cag_entitlement_when_org_is_not_entitled() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(jsonResponse("""
        {"hasEntitlement":false}
        """, 200)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNotNull();
    assertThat(entitlement.hasEntitlement()).isFalse();
  }

  @Test
  void it_should_return_has_entitlement_true_when_consumption_limit_reached() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(jsonResponse("""
        {"allowed":false,"hasEntitlement":true,"consumption":{"consumed":1000,"limit":1000}}
        """, 200)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNotNull();
    assertThat(entitlement.hasEntitlement()).isTrue();
  }

  @Test
  void it_should_default_has_entitlement_to_false_when_field_is_absent() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(jsonResponse("""
        {"allowed":true}
        """, 200)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNotNull();
    assertThat(entitlement.hasEntitlement()).isFalse();
  }

  @Test
  void it_should_return_null_on_cag_entitlement_server_error() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(aResponse().withStatus(500)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNull();
  }

  @Test
  void it_should_return_null_on_cag_entitlement_not_found() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(CAG_ENTITLEMENT_PUBLIC_PATH))
      .willReturn(aResponse().withStatus(404)));

    var entitlement = cagApi.getCagEntitlement(ORG_UUID);

    assertThat(entitlement).isNull();
  }
}
