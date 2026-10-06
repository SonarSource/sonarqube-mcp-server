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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OAuthProtectedResourceMetadataTest {

  @Test
  void should_challenge_for_initial_read_delegation_using_the_configured_public_host() {
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://sonarsource-dev9.eu.auth0.com/");
    assertThat(metadata.challenge()).isEqualTo("Bearer resource_metadata=\"https://api.sc-dev9.io/.well-known/oauth-protected-resource/mcp\", scope=\"read:all\"");
  }

  @ParameterizedTest
  @MethodSource("invalidUrls")
  void should_reject_missing_or_ambiguous_trust_configuration(String resource, String issuer) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthProtectedResourceMetadata(resource, issuer));
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

