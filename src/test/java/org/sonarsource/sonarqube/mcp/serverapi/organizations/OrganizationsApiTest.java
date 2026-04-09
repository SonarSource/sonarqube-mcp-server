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
package org.sonarsource.sonarqube.mcp.serverapi.organizations;

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
class OrganizationsApiTest {

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private OrganizationsApi organizationsApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    // For the organizations API, the base URL is used as-is (no sonarcloud.io host rewriting in tests)
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "my-org", null, true), httpClient);
    organizationsApi = new OrganizationsApi(helper);
  }

  @Test
  void it_should_return_uuid_v4_for_organization() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("""
        [{"id":"old-id","uuidV4":"550e8400-e29b-41d4-a716-446655440000"}]
        """, 200)));

    assertThat(organizationsApi.getOrganizationUuidV4("my-org")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  void it_should_return_null_when_response_is_empty_array() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(jsonResponse("[]", 200)));

    assertThat(organizationsApi.getOrganizationUuidV4("my-org")).isNull();
  }

  @Test
  void it_should_return_null_on_server_error() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(aResponse().withStatus(500)));

    assertThat(organizationsApi.getOrganizationUuidV4("my-org")).isNull();
  }

  @Test
  void it_should_return_null_on_forbidden() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(aResponse().withStatus(403)));

    assertThat(organizationsApi.getOrganizationUuidV4("my-org")).isNull();
  }

}
