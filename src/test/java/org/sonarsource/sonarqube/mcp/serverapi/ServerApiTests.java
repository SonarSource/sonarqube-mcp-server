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
import java.util.stream.Stream;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

  static Stream<Arguments> getErrorResponses() {
    return Stream.of(
      arguments(HttpStatus.SC_UNAUTHORIZED, UnauthorizedException.class,
        "SonarQube answered with Not authorized. Please check server credentials."),
      arguments(HttpStatus.SC_FORBIDDEN, ForbiddenException.class,
        "SonarQube answered with Forbidden"),
      arguments(HttpStatus.SC_NOT_FOUND, NotFoundException.class,
        "SonarQube answered with Error 404 on %s/test"),
      arguments(HttpStatus.SC_INTERNAL_SERVER_ERROR, ServerInternalErrorException.class,
        "SonarQube answered with Error 500 on %s/test"),
      arguments(HttpStatus.SC_BAD_REQUEST, IllegalStateException.class,
        "Error 400 on %s/test")
    );
  }

  @ParameterizedTest
  @MethodSource("getErrorResponses")
  void it_should_throw_appropriate_exception_on_error_response(int statusCode, Class<? extends Exception> expectedException, String messageTemplate) {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(statusCode)));
    var expectedMessage = messageTemplate.contains("%s") ? messageTemplate.formatted(sonarqubeMock.baseUrl()) : messageTemplate;

    var exception = assertThrows(expectedException, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage(expectedMessage);
  }

  static Stream<Arguments> errorBodyParsingCases() {
    return Stream.of(
      arguments("{\"errors\": [{\"msg\": \"Kaboom\"}]}", "Error 400 on %s/test: Kaboom"),
      arguments("{\"message\": \"Project sonarcloud-core doesn't have a valid ID\"}", "Error 400 on %s/test: Project sonarcloud-core doesn't have a valid ID"),
      arguments("{\"status\": \"error\"}", "Error 400 on %s/test")
    );
  }

  @ParameterizedTest
  @MethodSource("errorBodyParsingCases")
  void it_should_parse_error_message_from_body(String responseBody, String messageTemplate) {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse(responseBody, HttpStatus.SC_BAD_REQUEST)));

    var exception = assertThrows(IllegalStateException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage(messageTemplate.formatted(sonarqubeMock.baseUrl()));
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

  static Stream<Arguments> buildApiSubdomainUrlCases() {
    return Stream.of(
      // known hosts: sonarcloud.io and sonarqube.us get their api.* subdomain derived automatically
      arguments("https://sonarcloud.io", "my-org", null, "https://api.sonarcloud.io/api/test"),
      arguments("https://sonarqube.us", "my-org", null, "https://api.sonarqube.us/api/test"),
      // no org (SQS): always falls back to the base URL
      arguments("https://sonarqube.us", null, null, "https://sonarqube.us/api/test"),
      // explicit override: used as-is regardless of the base URL host
      arguments("https://test.sc-test.io", "my-org", "https://api.sc-test.io", "https://api.sc-test.io/api/test"),
      // unknown host without override: base URL is used as-is
      arguments("https://test.sc-test.io", "my-org", null, "https://test.sc-test.io/api/test")
    );
  }

  @ParameterizedTest
  @MethodSource("buildApiSubdomainUrlCases")
  void buildApiSubdomainUrl_should_build_correct_url(String baseUrl, String org, String apiBaseUrl, String expectedUrl) {
    var httpClient = new HttpClientProvider(USER_AGENT).getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(baseUrl, org, apiBaseUrl), httpClient);

    assertThat(helper.buildApiSubdomainUrl("/api/test")).isEqualTo(expectedUrl);
  }

  static Stream<Arguments> postApiSubdomainErrorResponses() {
    return Stream.of(
      arguments(HttpStatus.SC_UNAUTHORIZED, UnauthorizedException.class,
        "SonarQube answered with Not authorized. Please check server credentials."),
      arguments(HttpStatus.SC_FORBIDDEN, ForbiddenException.class,
        "SonarQube answered with Forbidden"),
      arguments(HttpStatus.SC_INTERNAL_SERVER_ERROR, ServerInternalErrorException.class,
        "SonarQube answered with Error 500 on %s/api/test")
    );
  }

  @ParameterizedTest
  @MethodSource("postApiSubdomainErrorResponses")
  void postApiSubdomain_should_throw_appropriate_exception_on_error_response(int statusCode, Class<? extends Exception> expectedException, String messageTemplate) {
    sonarqubeMock.stubFor(post("/api/test").willReturn(aResponse().withStatus(statusCode)));
    var expectedMessage = messageTemplate.contains("%s") ? messageTemplate.formatted(sonarqubeMock.baseUrl()) : messageTemplate;

    var exception = assertThrows(expectedException,
      () -> {
        try (var ignored = serverApiHelper.postApiSubdomain("/api/test", "application/json", "{}")) {
          // an exception is thrown before a response can be returned
        }
      });
    assertThat(exception).hasMessage(expectedMessage);
  }

}
