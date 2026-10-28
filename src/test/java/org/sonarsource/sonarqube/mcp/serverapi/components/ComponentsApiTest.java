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
package org.sonarsource.sonarqube.mcp.serverapi.components;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentsApiTest {

  private static final String LOOKUP = "/api/v2/components/projects?keys=shop%3Aapi&pageSize=1";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

  private HttpClientProvider httpClientProvider;
  private ComponentsApi api;

  @BeforeEach
  void init() {
    httpClientProvider = new HttpClientProvider("test");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false),
      httpClientProvider.getHttpClient("test-token"));
    api = new ComponentsApi(helper);
  }

  @AfterEach
  void closeHttpClient() {
    httpClientProvider.shutdown();
  }

  @Test
  void it_should_resolve_an_encoded_key_and_preserve_the_opaque_id() {
    sonarqubeMock.stubFor(get(LOOKUP).willReturn(okJson("""
      {"projects":[{"id":"opaque-project-id","key":"shop:api"}],"page":{"total":1}}
      """)));

    assertThat(api.getProjectId("shop:api")).isEqualTo("opaque-project-id");
    sonarqubeMock.verify(1, getRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(LOOKUP))
      .withHeader("Authorization", equalTo("Bearer test-token")));
    assertThat(sonarqubeMock.getAllServeEvents()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{}", "null", "{\"projects\":[],\"page\":{\"total\":0}}",
    "{\"projects\":[{\"id\":\"opaque\",\"key\":\"wrong\"}],\"page\":{\"total\":1}}",
    "{\"projects\":[{\"id\":12,\"key\":\"shop:api\"}],\"page\":{\"total\":1}}",
    "{\"projects\":[{\"id\":\"\",\"key\":\"shop:api\"}],\"page\":{\"total\":1}}",
    "{\"projects\":[{\"id\":\"opaque\",\"key\":\"shop:api\"}],\"page\":{\"total\":2}}"
  })
  void it_should_refuse_incomplete_or_mismatched_responses(String body) {
    sonarqubeMock.stubFor(get(LOOKUP).willReturn(okJson(body)));

    assertThatThrownBy(() -> api.getProjectId("shop:api")).isInstanceOf(IllegalStateException.class);
    assertThat(sonarqubeMock.getAllServeEvents()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404})
  void it_should_propagate_a_failed_lookup_without_fallback(int status) {
    sonarqubeMock.stubFor(get(LOOKUP).willReturn(aResponse().withStatus(status)));
    var expectedException = switch (status) {
      case 401 -> UnauthorizedException.class;
      case 403 -> ForbiddenException.class;
      default -> IllegalStateException.class;
    };
    assertThatThrownBy(() -> api.getProjectId("shop:api")).isInstanceOf(expectedException);
    assertThat(sonarqubeMock.getAllServeEvents()).hasSize(1);
  }
}
