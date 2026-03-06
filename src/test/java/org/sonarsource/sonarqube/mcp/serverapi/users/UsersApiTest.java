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
package org.sonarsource.sonarqube.mcp.serverapi.users;

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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UsersApiTest {

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private UsersApi usersApi;

  @BeforeAll
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null), httpClient);
    usersApi = new UsersApi(helper);
  }

  @Test
  void it_should_return_user_id_when_present() {
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH)
      .willReturn(jsonResponse("""
        {"id":"user-uuid-abc","login":"john","name":"John Doe"}
        """, 200)));

    assertThat(usersApi.getCurrentUserId()).isEqualTo("user-uuid-abc");
  }

  @Test
  void it_should_return_null_when_id_field_is_absent() {
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH)
      .willReturn(jsonResponse("""
        {"login":"john","name":"John Doe"}
        """, 200)));

    assertThat(usersApi.getCurrentUserId()).isNull();
  }

  @Test
  void it_should_return_null_on_unauthorized() {
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH)
      .willReturn(aResponse().withStatus(401)));

    assertThat(usersApi.getCurrentUserId()).isNull();
  }

  @Test
  void it_should_return_null_on_server_error() {
    sonarqubeMock.stubFor(get(UsersApi.CURRENT_USER_PATH)
      .willReturn(aResponse().withStatus(500)));

    assertThat(usersApi.getCurrentUserId()).isNull();
  }

}
