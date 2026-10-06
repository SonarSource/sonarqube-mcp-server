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
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
    exchange = new Auth0TokenExchange(configuration, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
      URI.create(auth0.baseUrl() + "/oauth/token"));
  }

  @AfterEach
  void tearDown() { auth0.stop(); }

  @Test
  void should_send_authenticated_native_exchange_with_explicit_exact_scopes() {
    auth0.stubFor(post("/oauth/token").atPriority(10)
      .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid_request\"}")));
    auth0.stubFor(post("/oauth/token").atPriority(1)
      .withFormParam("requested_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token"))
      .willReturn(aResponse().withBody("{\"access_token\":\"token-b\",\"token_type\":\"Bearer\"}")));
    assertThat(exchange.exchange("token-a", Set.of("read:all"))).isEqualTo("token-b");
    auth0.verify(postRequestedFor(urlEqualTo("/oauth/token"))
      .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
      .withFormParam("grant_type", equalTo("urn:ietf:params:oauth:grant-type:token-exchange"))
      .withFormParam("subject_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token"))
      .withFormParam("requested_token_type", equalTo("urn:ietf:params:oauth:token-type:access_token"))
      .withFormParam("subject_token", equalTo("token-a"))
      .withFormParam("audience", equalTo("https://api.sc-dev9.io/"))
      .withFormParam("scope", equalTo("read:all"))
      .withFormParam("client_id", equalTo("backend"))
      .withFormParam("client_secret", equalTo("secret&with=special+characters")));
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
      });
  }

  @Test
  void should_reject_malformed_oversized_or_non_bearer_responses() {
    for (var response : new String[]{"invalid-json", "{}", "{\"access_token\":\"b\",\"token_type\":\"Basic\"}", "a".repeat(65537)}) {
      auth0.stubFor(post("/oauth/token").willReturn(aResponse().withBody(response)));
      assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all")))
        .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(502));
    }
  }

  @Test
  void should_never_follow_exchange_redirects() {
    auth0.stubFor(post("/oauth/token").willReturn(aResponse().withStatus(307).withHeader("Location", auth0.baseUrl() + "/other")
      .withBody("{}")));
    assertThatThrownBy(() -> exchange.exchange("token-a", Set.of("read:all"))).isInstanceOf(OAuthAuthenticationException.class);
    auth0.verify(0, postRequestedFor(urlEqualTo("/other")));
  }
}

