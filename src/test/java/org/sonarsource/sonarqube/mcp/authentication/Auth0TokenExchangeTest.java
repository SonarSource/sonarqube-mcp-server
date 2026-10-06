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
package org.sonarsource.sonarqube.mcp.authentication;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Auth0TokenExchangeTest {
  private WireMockServer auth0;
  private Auth0TokenExchange exchange;

  @BeforeEach
  void setUp() {
    auth0 = new WireMockServer(wireMockConfig().dynamicPort());
    auth0.start();
    var configuration = new OAuthConfiguration("https://auth-dev9.sc-dev9.io/", "https://api.sc-dev9.io/mcp", "https://api.sc-dev9.io/",
      "backend", "secret&with=special+characters", Duration.ofSeconds(30), 256);
    exchange = new Auth0TokenExchange(configuration, auth0.baseUrl());
  }

  @AfterEach
  void tearDown() { auth0.stop(); }

  @ParameterizedTest
  @CsvSource({"read:all,read:all", "write:all,write:all", "write:all read:all,read:all write:all"})
  void should_send_authenticated_native_exchange_with_explicit_exact_scopes(String sourceScopes, String expectedScopes) {
    auth0.stubFor(post("/oauth/token").atPriority(10)
      .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid_request\"}")));
    auth0.stubFor(post("/oauth/token").atPriority(1)
      .withRequestBody(matchingJsonPath("$.requested_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token")))
      .willReturn(aResponse().withBody("{\"access_token\":\"token-b\",\"token_type\":\"Bearer\"}")));
    assertThat(exchange.exchange("token-a", Set.of(sourceScopes.split(" ")))).isEqualTo("token-b");
    auth0.verify(postRequestedFor(urlEqualTo("/oauth/token"))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(matchingJsonPath("$.grant_type", equalTo("urn:ietf:params:oauth:grant-type:token-exchange")))
      .withRequestBody(matchingJsonPath("$.subject_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token")))
      .withRequestBody(matchingJsonPath("$.requested_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token")))
      .withRequestBody(matchingJsonPath("$.subject_token", equalTo("token-a")))
      .withRequestBody(matchingJsonPath("$.audience", equalTo("https://api.sc-dev9.io/")))
      .withRequestBody(matchingJsonPath("$.scope", equalTo(expectedScopes)))
      .withRequestBody(matchingJsonPath("$.client_id", equalTo("backend")))
      .withRequestBody(matchingJsonPath("$.client_secret", equalTo("secret&with=special+characters"))));
  }

  @ParameterizedTest
  @CsvSource({"400,invalid_grant,401", "400,invalid_token,401", "400,invalid_scope,502", "401,invalid_client,502", "429,limit,503", "500,internal,503"})
  void should_distinguish_invalid_authorization_from_exchange_outage_without_leaking_details(int status, String error, int expected) {
    auth0.stubFor(post("/oauth/token").willReturn(aResponse().withStatus(status)
      .withBody("{\"error\":\"" + error + "\",\"error_description\":\"private-token-or-secret\"}")));
    assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all")))
      .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> {
        assertThat(failure.status()).isEqualTo(expected);
        assertThat(failure.toString()).doesNotContain("private-token-or-secret", "token-a");
        assertThat(failure).hasNoCause();
      });
  }

  @Test
  void should_reject_malformed_or_invalid_token_responses() {
    for (var response : new String[]{"invalid-json", "{}", "{\"access_token\":\"b\",\"token_type\":\"Basic\"}", "{\"access_token\":\"\",\"token_type\":\"Bearer\"}",
      "{\"access_token\":\"" + "a".repeat(16385) + "\",\"token_type\":\"Bearer\"}"}) {
      auth0.stubFor(post("/oauth/token").willReturn(aResponse().withBody(response)));
      assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all")))
        .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(502));
    }
  }

  @Test
  void should_never_follow_exchange_redirects() {
    auth0.stubFor(post("/oauth/token").willReturn(aResponse().withStatus(307).withHeader("Location", auth0.baseUrl() + "/other")
      .withBody("{}")));
    var scopes = Set.of("read:all");
    assertThatThrownBy(() -> exchange.exchange("token-a", scopes)).isInstanceOf(OAuthAuthenticationException.class);
    auth0.verify(0, postRequestedFor(urlEqualTo("/other")));
  }
  @Test
  void should_never_exchange_without_explicit_delegation_scopes() {
    assertThatThrownBy(() -> exchange.exchange("token-a", Set.of()))
      .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(502));
    auth0.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void should_not_retry_rate_limited_exchange() {
    auth0.stubFor(post("/oauth/token").willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")
      .withBody("{\"error\":\"too_many_requests\"}")));
    assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all")))
      .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(503));
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void should_accept_valid_token_inside_a_large_chunked_response() {
    auth0.stubFor(post("/oauth/token").willReturn(aResponse().withChunkedDribbleDelay(4, 10)
      .withBody("{\"access_token\":\"b\",\"token_type\":\"Bearer\",\"padding\":\"" + "a".repeat(65537) + "\"}")));
    assertThat(exchange.exchange("token-a", Set.of("read:all"))).isEqualTo("b");
  }

  @Test
  void should_preserve_caller_interruption_without_sending_exchange() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all")))
        .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(503));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    auth0.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
  }

}


