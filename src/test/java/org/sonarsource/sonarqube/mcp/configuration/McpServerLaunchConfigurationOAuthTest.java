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
package org.sonarsource.sonarqube.mcp.configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerLaunchConfigurationOAuthTest {

  @TempDir
  private Path storage;

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void it_should_load_explicit_oauth_configuration_without_a_fixed_organization(String transport) {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_TRANSPORT", transport);

    var configuration = new McpServerLaunchConfiguration(environment);

    assertThat(configuration.getAuthMode()).isEqualTo(AuthMode.OAUTH);
    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarqubeOrg()).isNull();
    assertThat(configuration.getSonarQubeToken()).isNull();
    var oauth = configuration.getOAuthConfiguration();
    assertThat(oauth).isNotNull();
    assertThat(oauth.resource()).isEqualTo("https://api.example.com/mcp");
    assertThat(oauth.issuer()).isEqualTo("https://login.example.com/");
    assertThat(oauth.cloudAudience()).isEqualTo("https://api.example.com/");
    assertThat(oauth.clientId()).isEqualTo("mcp-backend");
    assertThat(oauth.cacheTtl()).isEqualTo(Duration.ofSeconds(30));
    assertThat(oauth.cacheSize()).isEqualTo(256);
    assertThat(oauth.toString()).doesNotContain("client-secret");
    assertThat(configuration.getOAuthMetadata()).isNotNull();
    assertThat(configuration.getOAuthMetadata().isSelfHosted()).isTrue();
    assertThat(configuration.getOAuthMetadata().challenge())
      .contains("https://api.example.com/.well-known/oauth-protected-resource/mcp");
  }

  @Test
  void it_should_advertise_configured_external_metadata() {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_OAUTH_METADATA_URL", "https://discovery.example.com/mcp");

    var configuration = new McpServerLaunchConfiguration(environment);

    assertThat(configuration.getOAuthMetadata().isSelfHosted()).isFalse();
    assertThat(configuration.getOAuthMetadata().challenge()).contains("https://discovery.example.com/mcp");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "SONARQUBE_URL", "SONARQUBE_CLOUD_API_URL", "SONARQUBE_OAUTH_RESOURCE", "SONARQUBE_OAUTH_ISSUER",
    "SONARQUBE_OAUTH_CLOUD_AUDIENCE", "SONARQUBE_OAUTH_CLIENT_ID", "SONARQUBE_OAUTH_CLIENT_SECRET"
  })
  void it_should_require_explicit_oauth_trust_and_exchange_settings(String setting) {
    var environment = oauthEnvironment();
    environment.remove(setting);

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource({"0, 1", "30, 5000", "10, 100"})
  void it_should_apply_bounded_cache_settings(int ttl, int size) {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_OAUTH_CACHE_TTL_SECONDS", Integer.toString(ttl));
    environment.put("SONARQUBE_OAUTH_CACHE_SIZE", Integer.toString(size));

    var oauth = new McpServerLaunchConfiguration(environment).getOAuthConfiguration();

    assertThat(oauth.cacheTtl()).isEqualTo(Duration.ofSeconds(ttl));
    assertThat(oauth.cacheSize()).isEqualTo(size);
  }

  @ParameterizedTest
  @CsvSource({
    "SONARQUBE_OAUTH_CACHE_TTL_SECONDS, -1", "SONARQUBE_OAUTH_CACHE_TTL_SECONDS, 31",
    "SONARQUBE_OAUTH_CACHE_SIZE, 0", "SONARQUBE_OAUTH_CACHE_SIZE, 5001",
    "SONARQUBE_OAUTH_CACHE_TTL_SECONDS, invalid", "SONARQUBE_OAUTH_CACHE_SIZE, 2147483648"
  })
  void it_should_reject_invalid_cache_settings(String setting, String value) {
    var environment = oauthEnvironment();
    environment.put(setting, value);

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "http://cloud.example.com", "https://user:password@cloud.example.com", "https://cloud.example.com?token=secret",
    "https://cloud.example.com#fragment", "https:///missing-host"
  })
  void it_should_reject_unsafe_downstream_urls(String url) {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_URL", url);

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://other.example.com/", "http://api.example.com/", "https://api.example.com/v2/"})
  void it_should_reject_a_cloud_api_url_that_does_not_match_the_token_audience(String apiUrl) {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_CLOUD_API_URL", apiUrl);

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://api.example.com", "https://api.example.com/"})
  void it_should_accept_the_cloud_api_origin_with_an_optional_trailing_slash(String apiUrl) {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_CLOUD_API_URL", apiUrl);

    assertThat(new McpServerLaunchConfiguration(environment).getOAuthConfiguration()).isNotNull();
  }

  @Test
  void it_should_reject_oauth_for_sonarqube_server() {
    var environment = oauthEnvironment();
    environment.remove("SONARQUBE_IS_CLOUD");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Cloud");
  }

  @Test
  void it_should_keep_http_startup_organizations_prohibited() {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_ORG", "fixed-org");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("SONARQUBE_ORG");
  }

  @Test
  void it_should_preserve_the_optional_startup_token() {
    var environment = oauthEnvironment();
    environment.put("SONARQUBE_TOKEN", "startup-token");

    assertThat(new McpServerLaunchConfiguration(environment).getSonarQubeToken()).isEqualTo("startup-token");
  }

  @Test
  void it_should_ignore_oauth_settings_for_token_authentication() {
    var environment = new HashMap<>(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TRANSPORT", "http",
      "SONARQUBE_HTTP_AUTH_MODE", "TOKEN", "SONARQUBE_OAUTH_RESOURCE", "invalid"));

    var configuration = new McpServerLaunchConfiguration(environment);

    assertThat(configuration.getAuthMode()).isEqualTo(AuthMode.TOKEN);
    assertThat(configuration.getOAuthConfiguration()).isNull();
    assertThat(configuration.getOAuthMetadata()).isNull();
  }

  @Test
  void it_should_ignore_http_oauth_settings_in_stdio_mode() {
    var configuration = new McpServerLaunchConfiguration(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TOKEN", "token",
      "SONARQUBE_HTTP_AUTH_MODE", "OAUTH", "SONARQUBE_OAUTH_RESOURCE", "invalid"));

    assertThat(configuration.isHttpEnabled()).isFalse();
    assertThat(configuration.getAuthMode()).isNull();
    assertThat(configuration.getOAuthConfiguration()).isNull();
    assertThat(configuration.getOAuthMetadata()).isNull();
  }

  private Map<String, String> oauthEnvironment() {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_AUTH_MODE", "OAUTH");
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_URL", "https://cloud.example.com");
    environment.put("SONARQUBE_CLOUD_API_URL", "https://api.example.com/");
    environment.put("SONARQUBE_OAUTH_RESOURCE", "https://api.example.com/mcp");
    environment.put("SONARQUBE_OAUTH_ISSUER", "https://login.example.com/");
    environment.put("SONARQUBE_OAUTH_CLOUD_AUDIENCE", "https://api.example.com/");
    environment.put("SONARQUBE_OAUTH_CLIENT_ID", "mcp-backend");
    environment.put("SONARQUBE_OAUTH_CLIENT_SECRET", "client-secret");
    return environment;
  }
}
