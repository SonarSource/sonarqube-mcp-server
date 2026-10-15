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

import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthProtectedResourceMetadataTest {

  @Test
  void it_should_challenge_for_both_delegation_scopes_using_the_configured_public_host() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://sonarsource-dev9.eu.auth0.com/");
    assertThat(metadata.challenge()).isEqualTo("Bearer resource_metadata=\"https://api.sc-dev9.io/.well-known/oauth-protected-resource/mcp\", scope=\"read:all write:all\"");
    assertThat(metadata.isSelfHosted()).isTrue();
  }

  @Test
  void it_should_advertise_an_external_metadata_owner() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://auth-dev9.sc-dev9.io/",
      "https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp");
    assertThat(metadata.challenge()).isEqualTo("Bearer resource_metadata=\"https://api.sc-dev9.io/authentication/.well-known/oauth-protected-resource/mcp\", scope=\"read:all write:all\"");
    assertThat(metadata.isSelfHosted()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"read:all", "write:all"})
  void it_should_challenge_only_for_the_operation_required_scope(String scope) {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");

    assertThat(metadata.challenge(Set.of(scope)))
      .isEqualTo("Bearer resource_metadata=\"https://api.example.com/.well-known/oauth-protected-resource/mcp\", scope=\"" + scope + "\"");
  }

  @Test
  void it_should_sort_combined_challenge_scopes() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");

    assertThat(metadata.challenge(Set.of("write:all", "read:all"))).isEqualTo(metadata.challenge());
  }

  @Test
  void it_should_reject_empty_or_unsupported_challenge_scopes() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");

    assertThatIllegalArgumentException().isThrownBy(() -> metadata.challenge(Set.of()));
    assertThatIllegalArgumentException().isThrownBy(() -> metadata.challenge(Set.of("openid")));
  }

  @Test
  void it_should_serve_the_public_resource_metadata_document() throws Exception {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var output = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(output));

    metadata.doGet(request, response);

    verify(response).setContentType("application/json");
    verify(response).setHeader("Cache-Control", "public, max-age=300");
    assertThat(JsonParser.parseString(output.toString())).isEqualTo(JsonParser.parseString("""
      {
        "resource": "https://api.example.com/mcp",
        "authorization_servers": ["https://tenant.auth0.com/"],
        "scopes_supported": ["read:all", "write:all"],
        "bearer_methods_supported": ["header"]
      }
      """));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "http://api.example.com/metadata", "/.well-known/oauth-protected-resource/mcp",
    "https://user:secret@api.example.com/metadata", "https://api.example.com/metadata?resource=mcp", "https://api.example.com/metadata#fragment"})
  void it_should_reject_unsafe_external_metadata_urls(String url) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/", url));
  }

  @ParameterizedTest
  @MethodSource("invalidUrls")
  void it_should_reject_missing_or_ambiguous_trust_configuration(String resource, String issuer) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthProtectedResourceMetadata(resource, issuer));
  }

  @Test
  void it_should_handle_metadata_writer_failure_without_propagating_it() throws Exception {
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
      Arguments.of("", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/%6dcp", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", null),
      Arguments.of("https://api.example.com/mcp", "https://tenant.auth0.com/%2f"),
      Arguments.of("http://api.example.com/mcp", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "http://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp/", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "https://tenant.auth0.com"),
      Arguments.of("https://user:secret@api.example.com/mcp", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp?audience=other", "https://tenant.auth0.com/"),
      Arguments.of("https://api.example.com/mcp", "https://tenant.auth0.com/#other"));
  }
}
