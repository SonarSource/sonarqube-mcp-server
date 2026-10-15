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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OAuthConfigurationTest {

  private static final String ISSUER = "https://tenant.auth0.com/";
  private static final String RESOURCE = "https://api.example.com/mcp";
  private static final String AUDIENCE = "https://api.example.com/";

  @Test
  void it_should_default_to_a_30_second_cache_with_256_entries() {
    var configuration = new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "backend", "secret");

    assertThat(configuration.issuer()).isEqualTo(ISSUER);
    assertThat(configuration.resource()).isEqualTo(RESOURCE);
    assertThat(configuration.cloudAudience()).isEqualTo(AUDIENCE);
    assertThat(configuration.clientId()).isEqualTo("backend");
    assertThat(configuration.clientSecret()).isEqualTo("secret");
    assertThat(configuration.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(configuration.cacheSize()).isEqualTo(256);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "${UNRESOLVED}"})
  void it_should_require_explicit_credentials(String credential) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, credential, "secret"));
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "backend", credential));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"${AUDIENCE}", "http://api.example.com/", "https://api.example.com", "https://api.example.com/mcp",
    "https://api.other.com/", "https://api.example.com:8443/", "https://user:secret@api.example.com/",
    "https://api.example.com/?audience=other", "https://api.example.com/#other"})
  void it_should_reject_invalid_or_different_cloud_api_origins(String audience) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration(ISSUER, RESOURCE, audience, "backend", "secret"));
  }

  @Test
  void it_should_validate_the_resource_and_issuer_without_metadata_registration() {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration(ISSUER, "http://api.example.com/mcp", AUDIENCE, "backend", "secret"));
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration("http://tenant.auth0.com/", RESOURCE, AUDIENCE, "backend", "secret"));
  }

  @ParameterizedTest
  @MethodSource("invalidCacheSettings")
  void it_should_reject_cache_settings_outside_the_bounds(Duration ttl, int size) {
    assertThatIllegalArgumentException().isThrownBy(() -> new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "backend", "secret", ttl, size));
  }

  static Stream<Arguments> invalidCacheSettings() {
    return Stream.of(
      Arguments.of(null, 256),
      Arguments.of(Duration.ofNanos(-1), 256),
      Arguments.of(Duration.ofSeconds(30).plusNanos(1), 256),
      Arguments.of(Duration.ofSeconds(60), 256),
      Arguments.of(Duration.ofSeconds(30), 0),
      Arguments.of(Duration.ofSeconds(30), 5001));
  }

  @Test
  void it_should_accept_maximum_cache_bounds() {
    var configuration = new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "backend", "secret", Duration.ofSeconds(30), 5000);

    assertThat(configuration.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(configuration.cacheSize()).isEqualTo(5000);
  }

  @Test
  void it_should_accept_zero_ttl_and_minimum_cache_size() {
    var configuration = new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "backend", "secret", Duration.ZERO, 1);

    assertThat(configuration.cacheTtl()).isEqualTo(Duration.ZERO);
    assertThat(configuration.cacheSize()).isEqualTo(1);
  }

  @Test
  void it_should_keep_credentials_out_of_diagnostics() {
    var configuration = new OAuthConfiguration(ISSUER, RESOURCE, AUDIENCE, "sensitive-client-id", "sensitive-secret");

    assertThat(configuration.toString()).contains(ISSUER, RESOURCE, AUDIENCE, "credentials=redacted")
      .doesNotContain("sensitive-client-id", "sensitive-secret");
  }
}
