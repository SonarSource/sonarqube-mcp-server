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
package org.sonarsource.sonarqube.mcp.serverapi;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerApiTests {

  private static final String USER_AGENT = "SonarQube MCP tests";
  private ServerApiHelper serverApiHelper;
  private PrintStream originalErr;
  private ByteArrayOutputStream errBuffer;

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

  @AfterEach
  void tearDown() {
    System.clearProperty("SONARQUBE_DEBUG_ENABLED");
    if (originalErr != null) {
      System.setErr(originalErr);
      originalErr = null;
      errBuffer = null;
    }
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

  @Test
  void it_should_parse_the_message_field_in_the_body_when_there_is_an_error() {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"message\": \"Project sonarcloud-core doesn't have a valid ID\"}", HttpStatus.SC_BAD_REQUEST)));

    var exception = assertThrows(IllegalStateException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("Error 400 on " + sonarqubeMock.baseUrl() + "/test: Project sonarcloud-core doesn't have a valid ID");
  }

  @Test
  void it_should_ignore_body_when_json_has_no_errors_or_message() {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"status\": \"error\"}", HttpStatus.SC_BAD_REQUEST)));

    var exception = assertThrows(IllegalStateException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("Error 400 on " + sonarqubeMock.baseUrl() + "/test");
  }

  @Test
  void it_should_log_error_response_code() {
    System.setProperty("SONARQUBE_DEBUG_ENABLED", "true");
    originalErr = System.err;
    errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"errors\": [{\"msg\": \"Missing permission\",\"code\":\"insufficient_privileges\"}]}", HttpStatus.SC_FORBIDDEN)));

    assertThrows(ForbiddenException.class, () -> serverApiHelper.get("/test"));

    var stderrOutput = errBuffer.toString(StandardCharsets.UTF_8);
    assertThat(stderrOutput)
      .contains("HTTP error - URL: " + sonarqubeMock.baseUrl() + "/test")
      .contains("status: 403");
  }

  @Test
  void postApiSubdomain_should_return_response_on_success() {
    sonarqubeMock.stubFor(post("/api/test").willReturn(aResponse()
      .withStatus(HttpStatus.SC_OK)
      .withBody("{\"result\": \"ok\"}")));

    try (var response = serverApiHelper.postApiSubdomain("/api/test", "application/json", "{\"data\": \"test\"}")) {
      assertThat(response.isSuccessful()).isTrue();
      assertThat(response.bodyAsString()).isEqualTo("{\"result\": \"ok\"}");
    }
  }

  @Test
  void buildApiSubdomainUrl_should_rewrite_sonarqube_us_to_api_subdomain() {
    var httpClientProvider = new HttpClientProvider(USER_AGENT);
    var httpClient = httpClientProvider.getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams("https://sonarqube.us", "my-org"), httpClient);

    var url = helper.buildApiSubdomainUrl("/api/test");

    assertThat(url).isEqualTo("https://api.sonarqube.us/api/test");
  }

  @Test
  void buildApiSubdomainUrl_should_use_regular_endpoint_when_no_org() {
    var httpClientProvider = new HttpClientProvider(USER_AGENT);
    var httpClient = httpClientProvider.getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams("https://sonarqube.us", null), httpClient);

    var url = helper.buildApiSubdomainUrl("/api/test");

    assertThat(url).isEqualTo("https://sonarqube.us/api/test");
  }

  @Test
  void postApiSubdomain_should_throw_on_unauthorized_response() {
    sonarqubeMock.stubFor(post("/api/test").willReturn(aResponse().withStatus(HttpStatus.SC_UNAUTHORIZED)));

    var exception = assertThrows(UnauthorizedException.class,
      () -> serverApiHelper.postApiSubdomain("/api/test", "application/json", "{}"));
    assertThat(exception).hasMessage("SonarQube answered with Not authorized. Please check server credentials.");
  }

  @Test
  void postApiSubdomain_should_throw_on_forbidden_response() {
    sonarqubeMock.stubFor(post("/api/test").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));

    var exception = assertThrows(ForbiddenException.class,
      () -> serverApiHelper.postApiSubdomain("/api/test", "application/json", "{}"));
    assertThat(exception).hasMessage("SonarQube answered with Forbidden");
  }

  @Test
  void postApiSubdomain_should_throw_on_internal_error_response() {
    sonarqubeMock.stubFor(post("/api/test").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));

    var exception = assertThrows(ServerInternalErrorException.class,
      () -> serverApiHelper.postApiSubdomain("/api/test", "application/json", "{}"));
    assertThat(exception).hasMessage("SonarQube answered with Error 500 on " + sonarqubeMock.baseUrl() + "/api/test");
  }

}
