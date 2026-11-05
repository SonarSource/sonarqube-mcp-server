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
package org.sonarsource.sonarqube.mcp.serverapi.settings;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsApiTests {

  private static final String KEY = "sonar.architecture.model.location";
  private static final String VALUES = SettingsApi.SETTINGS_VALUES_PATH + "?keys=" + KEY + "&component=shop%3Aapi";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

  private HttpClientProvider httpClientProvider;
  private SettingsApi api;

  @BeforeEach
  void init() {
    httpClientProvider = new HttpClientProvider("test");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false),
      httpClientProvider.getHttpClient("test-token"));
    api = new SettingsApi(helper);
  }

  @AfterEach
  void closeHttpClient() {
    httpClientProvider.shutdown();
  }

  @Test
  void it_should_read_the_value_of_the_requested_key() {
    sonarqubeMock.stubFor(get(VALUES).willReturn(okJson("""
      {"settings":[{"key":"other","value":"SERVER"},{"key":"sonar.architecture.model.location","value":"FILE","inherited":true}],"setSecuredSettings":[]}
      """)));

    assertThat(api.getValue("shop:api", KEY)).contains("FILE");
  }

  @Test
  void it_should_return_empty_when_the_setting_is_not_returned() {
    sonarqubeMock.stubFor(get(VALUES).willReturn(okJson("{\"settings\":[]}")));

    assertThat(api.getValue("shop:api", KEY)).isEmpty();
  }

  @Test
  void it_should_return_empty_when_the_response_has_no_settings() {
    sonarqubeMock.stubFor(get(VALUES).willReturn(okJson("{}")));

    assertThat(api.getValue("shop:api", KEY)).isEmpty();
  }

  @Test
  void it_should_propagate_access_errors() {
    sonarqubeMock.stubFor(get(VALUES).willReturn(aResponse().withStatus(403)));

    assertThatThrownBy(() -> api.getValue("shop:api", KEY)).isInstanceOf(ForbiddenException.class);
  }

}
