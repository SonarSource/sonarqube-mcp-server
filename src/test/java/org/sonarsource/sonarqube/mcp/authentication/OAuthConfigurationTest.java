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

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthConfigurationTest {
  @ParameterizedTest
  @MethodSource("invalidSettings")
  void should_reject_missing_credentials_insecure_audience_or_unbounded_cache(String audience, String secret, long ttl, int size) {
    assertThatThrownBy(() -> new OAuthConfiguration("https://auth-dev9.sc-dev9.io/", "https://api.sc-dev9.io/mcp", audience,
      "backend", secret, Duration.ofSeconds(ttl), size)).isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> invalidSettings() {
    return Stream.of(Arguments.of("http://api.sc-dev9.io/", "secret", 30, 256),
      Arguments.of("https://api.sonarcloud.io/", "secret", 30, 256), Arguments.of("https://api.sc-dev9.io/", "", 30, 256),
      Arguments.of("https://api.sc-dev9.io/", "${SECRET}", 30, 256), Arguments.of("https://api.sc-dev9.io/", "secret", 31, 256),
      Arguments.of("https://api.sc-dev9.io/", "secret", -1, 256), Arguments.of("https://api.sc-dev9.io/", "secret", 30, 0),
      Arguments.of("https://api.sc-dev9.io/", "secret", 30, 1025));
  }

  @Test
  void should_keep_credential_out_of_diagnostics() {
    var configuration = new OAuthConfiguration("https://auth-dev9.sc-dev9.io/", "https://api.sc-dev9.io/mcp", "https://api.sc-dev9.io/",
      "backend", "sensitive-secret", Duration.ZERO, 1);
    assertThat(configuration.toString()).contains("credentials=redacted").doesNotContain("sensitive-secret");
  }
}

