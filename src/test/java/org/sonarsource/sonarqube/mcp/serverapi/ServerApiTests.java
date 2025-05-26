/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerApiTests {

  private static final String USER_AGENT = "Sonar MCP tests";
  private ServerApiHelper serverApiHelper;

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeAll
  void init() {
    var httpClientProvider = new HttpClientProvider(USER_AGENT);
    var httpClient = httpClientProvider.getHttpClient("token");

    serverApiHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "org"), httpClient);
  }

  @Test
  void it_should_throw_on_unauthorized_response() {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_UNAUTHORIZED)));

    var exception = assertThrows(UnauthorizedException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with Not authorized. Please check server credentials.");
  }

  @Test
  void it_should_throw_on_forbidden_response() {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));

    var exception = assertThrows(ForbiddenException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with Forbidden");
  }

  @Test
  void it_should_throw_on_not_found_response() {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));

    var exception = assertThrows(NotFoundException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with Error 404 on " + sonarqubeMock.baseUrl() + "/test");
  }

  @Test
  void it_should_throw_on_internal_error_response() {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));

    var exception = assertThrows(ServerInternalErrorException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with Error 500 on " + sonarqubeMock.baseUrl() + "/test");
  }

  @Test
  void it_should_throw_on_any_other_error_response() {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_BAD_REQUEST)));

    var exception = assertThrows(IllegalStateException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("Error 400 on " + sonarqubeMock.baseUrl() + "/test");
  }

  @Test
  void it_should_parse_the_message_in_the_body_when_there_is_an_error() {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"errors\": [{\"msg\": \"Kaboom\"}]}", HttpStatus.SC_BAD_REQUEST)));

    var exception = assertThrows(IllegalStateException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("Error 400 on " + sonarqubeMock.baseUrl() + "/test: Kaboom");
  }

}
