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
package org.sonarsource.sonarqube.mcp.serverapi;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    serverApiHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "org", null, true), httpClient);
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
    sonarqubeMock.stubFor(post("/test").willReturn(aResponse().withStatus(statusCode)));
    var expectedMessage = messageTemplate.contains("%s") ? messageTemplate.formatted(sonarqubeMock.baseUrl()) : messageTemplate;

    var exception = assertThrows(expectedException, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage(expectedMessage);
    var postException = assertThrows(expectedException, () -> serverApiHelper.post("/test", "application/json", "{}", Duration.ofSeconds(5)));
    assertThat(postException).hasMessage(expectedMessage);
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

  static Stream<Arguments> invalidForbiddenBodies() {
    return Stream.of(
      arguments("<html>Forbidden</html>"),
      arguments(""),
      arguments("{"),
      arguments("[]"),
      arguments("{\"errors\": {\"msg\": \"No access\"}}"),
      arguments("{\"errors\": [null]}"),
      arguments("{\"errors\": [{}]}"),
      arguments("{\"errors\": [{\"msg\": {}}]}"),
      arguments("{\"message\": []}")
    );
  }

  @ParameterizedTest
  @MethodSource("invalidForbiddenBodies")
  void it_should_keep_forbidden_status_when_body_has_no_valid_message(String responseBody) {
    sonarqubeMock.stubFor(get("/test").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody(responseBody)));

    var exception = assertThrows(ForbiddenException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with Forbidden");
  }

  @Test
  void it_should_preserve_valid_forbidden_messages() {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"errors\": [{\"msg\": \"No access\"}, {\"msg\": \"Contact an admin\"}]}", HttpStatus.SC_FORBIDDEN)));

    var exception = assertThrows(ForbiddenException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with No access, Contact an admin");
  }

  @Test
  void it_should_use_valid_message_when_errors_member_is_invalid() {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"errors\": {}, \"message\": \"No access\"}", HttpStatus.SC_FORBIDDEN)));

    var exception = assertThrows(ForbiddenException.class, () -> serverApiHelper.get("/test"));
    assertThat(exception).hasMessage("SonarQube answered with No access");
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 405, 409, 412, 422, 429})
  void it_should_retain_definite_client_error_status(int status) {
    sonarqubeMock.stubFor(get("/test").willReturn(jsonResponse("{\"message\":\"Rejected\"}", status)));
    var failure = assertThrows(ClientErrorException.class, () -> serverApiHelper.get("/test"));
    assertThat(failure.status()).isEqualTo(status);
    assertThat(failure.getMessage()).contains("Error " + status, "Rejected");
  }

  @ParameterizedTest
  @ValueSource(ints = {HttpStatus.SC_BAD_REQUEST, HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_NOT_FOUND, HttpStatus.SC_INTERNAL_SERVER_ERROR})
  void it_should_close_failed_responses(int statusCode) {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(statusCode);
    when(response.bodyAsString()).thenReturn("<html>Error</html>");
    when(response.url()).thenReturn("https://example.com/test");

    ServerApiHelper.handleError(response);

    verify(response).close();
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
  void it_should_return_a_post_response_with_an_explicit_timeout() {
    sonarqubeMock.stubFor(post("/test").willReturn(jsonResponse("{\"result\":\"ok\"}", HttpStatus.SC_OK)));

    try (var response = serverApiHelper.post("/test", "application/json", "{}", Duration.ofSeconds(5))) {
      assertThat(response.bodyAsString()).isEqualTo("{\"result\":\"ok\"}");
    }
  }

  @Test
  void deleteApiSubdomain_should_return_a_successful_response_once() {
    sonarqubeMock.stubFor(delete("/api/test").willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

    try (var response = serverApiHelper.deleteApiSubdomain("/api/test")) {
      assertThat(response.code()).isEqualTo(HttpStatus.SC_NO_CONTENT);
    }
    sonarqubeMock.verify(1, deleteRequestedFor(urlEqualTo("/api/test")));
  }

  @ParameterizedTest
  @MethodSource("postApiSubdomainErrorResponses")
  void deleteApiSubdomain_should_reject_errors_without_replay(int statusCode, Class<? extends Exception> expectedException, String messageTemplate) {
    sonarqubeMock.stubFor(delete("/api/test").willReturn(aResponse().withStatus(statusCode)));
    var expectedMessage = messageTemplate.contains("%s") ? messageTemplate.formatted(sonarqubeMock.baseUrl()) : messageTemplate;

    var exception = assertThrows(expectedException, () -> serverApiHelper.deleteApiSubdomain("/api/test"));

    assertThat(exception).hasMessage(expectedMessage);
    sonarqubeMock.verify(1, deleteRequestedFor(urlEqualTo("/api/test")));
  }

  static Stream<Arguments> buildApiSubdomainUrlCases() {
    return Stream.of(
      // SQC - known hosts: api.* subdomain is derived automatically
      arguments("https://sonarcloud.io", "my-org", null, true, "https://api.sonarcloud.io/api/test"),
      arguments("https://sonarqube.us", "my-org", null, true, "https://api.sonarqube.us/api/test"),
      // SQC - no org (e.g. SONARQUBE_IS_CLOUD=true without SONARQUBE_ORG): subdomain is still derived
      arguments("https://sonarcloud.io", null, null, true, "https://api.sonarcloud.io/api/test"),
      // SQS - no isSonarQubeCloud: always falls back to the base URL
      arguments("https://my-sonarqube.example.com", null, null, false, "https://my-sonarqube.example.com/api/test"),
      // explicit override wins regardless of org or isSonarQubeCloud
      arguments("https://test.sc-test.io", "my-org", "https://api.sc-test.io", true, "https://api.sc-test.io/api/test"),
      arguments("https://test.sc-test.io", null, "https://api.sc-test.io", true, "https://api.sc-test.io/api/test"),
      // SQC - unknown host without override: base URL is used as-is
      arguments("https://test.sc-test.io", "my-org", null, true, "https://test.sc-test.io/api/test")
    );
  }

  @ParameterizedTest
  @MethodSource("buildApiSubdomainUrlCases")
  void buildApiSubdomainUrl_should_build_correct_url(String baseUrl, String org, String apiBaseUrl, boolean isSonarQubeCloud, String expectedUrl) {
    var httpClient = new HttpClientProvider(USER_AGENT).getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams(baseUrl, org, apiBaseUrl, isSonarQubeCloud), httpClient);

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
