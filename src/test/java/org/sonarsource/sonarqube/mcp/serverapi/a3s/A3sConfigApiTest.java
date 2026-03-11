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
package org.sonarsource.sonarqube.mcp.serverapi.a3s;

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
class A3sConfigApiTest {

  private static final String ORG_UUID = "57f08a8b-4a6e-4c64-bf72-83a892472f22";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private A3sConfigApi a3sConfigApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "my-org"), httpClient);
    a3sConfigApi = new A3sConfigApi(helper);
  }

  @Test
  void it_should_return_config_when_org_is_enabled() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sConfigApi.ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(jsonResponse("""
        {"id":"%s","enabled":true,"eligible":true}
        """.formatted(ORG_UUID), 200)));

    var config = a3sConfigApi.getOrgConfig(ORG_UUID);

    assertThat(config).isNotNull();
    assertThat(config.enabled()).isTrue();
    assertThat(config.eligible()).isTrue();
  }

  @Test
  void it_should_return_config_when_org_is_disabled() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sConfigApi.ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(jsonResponse("""
        {"id":"%s","enabled":false,"eligible":true}
        """.formatted(ORG_UUID), 200)));

    var config = a3sConfigApi.getOrgConfig(ORG_UUID);

    assertThat(config).isNotNull();
    assertThat(config.enabled()).isFalse();
  }

  @Test
  void it_should_return_null_on_server_error() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sConfigApi.ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(aResponse().withStatus(500)));

    var config = a3sConfigApi.getOrgConfig(ORG_UUID);

    assertThat(config).isNull();
  }

  @Test
  void it_should_return_null_on_not_found() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(A3sConfigApi.ORG_CONFIG_PATH + ORG_UUID))
      .willReturn(aResponse().withStatus(404)));

    var config = a3sConfigApi.getOrgConfig(ORG_UUID);

    assertThat(config).isNull();
  }

}
