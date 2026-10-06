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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OAuthProtectedResourceMetadataTest {

  @Test
  void should_challenge_for_initial_read_delegation_using_the_configured_public_host() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://sonarsource-dev9.eu.auth0.com/");
    assertThat(metadata.challenge()).isEqualTo("Bearer resource_metadata=\"https://api.sc-dev9.io/.well-known/oauth-protected-resource/mcp\", scope=\"read:all\"");
    assertThat(metadata.isSelfHosted()).isTrue();
  }

  @Test
  void should_advertise_an_external_owner_without_serving_a_duplicate_document() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://auth-dev9.sc-dev9.io/",
      "https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp");
    assertThat(metadata.challenge()).isEqualTo("Bearer resource_metadata=\"https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp\", scope=\"read:all\"");
    assertThat(metadata.isSelfHosted()).isFalse();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"", "http://api.example.com/metadata", "/.well-known/oauth-protected-resource/mcp",
    "https://user:secret@api.example.com/metadata", "https://api.example.com/metadata?resource=mcp", "https://api.example.com/metadata#fragment"})
  void should_reject_unsafe_external_metadata_urls(String url) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/", url));
  }

  @ParameterizedTest
  @MethodSource("invalidUrls")
  void should_reject_missing_or_ambiguous_trust_configuration(String resource, String issuer) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthProtectedResourceMetadata(resource, issuer));
  }

  @Test
  void should_handle_metadata_writer_failure_without_propagating_it() throws Exception {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenThrow(new IOException("connection closed"));

    assertThatCode(() -> metadata.doGet(request, response)).doesNotThrowAnyException();

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  static Stream<Arguments> invalidUrls() {
    return Stream.of(
      Arguments.of(null, "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", null),
      Arguments.of("http://api.example.com/mcp", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "http://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp/", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "https://tenant.auth0.com"),
      Arguments.of("https://user:secret@api.example.com/mcp", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp?audience=other", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "https://tenant.auth0.com/#other"));
  }
}


