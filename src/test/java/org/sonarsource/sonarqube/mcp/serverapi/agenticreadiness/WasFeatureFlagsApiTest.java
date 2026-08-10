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
package org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness;

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
class WasFeatureFlagsApiTest {

  private static final String ORG_ID = "57f08a8b-4a6e-4c64-bf72-83a892472f22";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private WasFeatureFlagsApi wasFeatureFlagsApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "my-org", null, true), httpClient);
    wasFeatureFlagsApi = new WasFeatureFlagsApi(helper);
  }

  @Test
  void it_should_return_true_when_flag_is_enabled() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(jsonResponse("""
        {"%s":true}
        """.formatted(WasFeatureFlagsApi.SARA_FEATURE_FLAG_KEY), 200)));

    assertThat(wasFeatureFlagsApi.isAgenticReadinessAssessmentEnabled(ORG_ID)).isTrue();
  }

  @Test
  void it_should_return_false_when_flag_is_absent() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(jsonResponse("{}", 200)));

    assertThat(wasFeatureFlagsApi.isAgenticReadinessAssessmentEnabled(ORG_ID)).isFalse();
  }

  @Test
  void it_should_return_false_on_server_error() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(aResponse().withStatus(500)));

    assertThat(wasFeatureFlagsApi.isAgenticReadinessAssessmentEnabled(ORG_ID)).isFalse();
  }

  @Test
  void it_should_return_false_on_not_found() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .willReturn(aResponse().withStatus(404)));

    assertThat(wasFeatureFlagsApi.isAgenticReadinessAssessmentEnabled(ORG_ID)).isFalse();
  }
}
