/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerLaunchConfigurationHttpTest {

  @Test
  void should_default_http_to_disabled() {
    var environment = createMinimalTestEnvironment();
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isFalse();
    assertThat(config.getHttpPort()).isEqualTo(8080);
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_parse_http_enabled_true() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isTrue();
    assertThat(config.getHttpPort()).isEqualTo(8080);
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_parse_http_enabled_false() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "false");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isFalse();
  }

  @Test
  void should_parse_custom_http_port() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "9000");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isTrue();
    assertThat(config.getHttpPort()).isEqualTo(9000);
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_parse_custom_http_host() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_HOST", "0.0.0.0");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isTrue();
    assertThat(config.getHttpPort()).isEqualTo(8080);
    assertThat(config.getHttpHost()).isEqualTo("0.0.0.0");
  }

  @Test
  void should_parse_full_http_configuration() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "8888");
    environment.put("SONARQUBE_HTTP_HOST", "localhost");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.isHttpEnabled()).isTrue();
    assertThat(config.getHttpPort()).isEqualTo(8888);
    assertThat(config.getHttpHost()).isEqualTo("localhost");
  }

  @Test
  void should_handle_missing_http_port_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    // SONARQUBE_HTTP_PORT is not set
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpPort()).isEqualTo(8080);
  }

  @Test
  void should_handle_empty_http_port_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpPort()).isEqualTo(8080);
  }

  @Test
  void should_handle_blank_http_port_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "   ");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpPort()).isEqualTo(8080);
  }

  @Test
  void should_validate_http_port_bounds_minimum() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_PORT", "1023");
    
    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SONARQUBE_HTTP_PORT value must be between 1024 and 65535 (unprivileged ports only), got: 1023");
  }

  @Test
  void should_reject_privileged_ports() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_PORT", "80");
    
    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SONARQUBE_HTTP_PORT value must be between 1024 and 65535 (unprivileged ports only)");
  }

  @Test
  void should_accept_valid_http_port_bounds() {
    var environment = createMinimalTestEnvironment();
    
    // Test minimum valid unprivileged port
    environment.put("SONARQUBE_HTTP_PORT", "1024");
    var config1 = new McpServerLaunchConfiguration(environment);
    assertThat(config1.getHttpPort()).isEqualTo(1024);
    
    // Test maximum valid port
    environment.put("SONARQUBE_HTTP_PORT", "65535");
    var config2 = new McpServerLaunchConfiguration(environment);
    assertThat(config2.getHttpPort()).isEqualTo(65535);
  }

  @Test
  void should_reject_invalid_http_port_format() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_PORT", "not-a-number");
    
    assertThatThrownBy(() -> new McpServerLaunchConfiguration(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid SONARQUBE_HTTP_PORT value: not-a-number");
  }

  @Test
  void should_handle_missing_http_host_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    // SONARQUBE_HTTP_HOST is not set
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_handle_empty_http_host_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_HOST", "");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_handle_blank_http_host_with_default() {
    var environment = createMinimalTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_HOST", "   ");
    
    var config = new McpServerLaunchConfiguration(environment);
    
    assertThat(config.getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_parse_various_boolean_values_for_http_enabled() {
    var environment = createMinimalTestEnvironment();
    
    // Test true values
    for (var trueValue : new String[]{"true", "True", "TRUE"}) {
      environment.put("SONARQUBE_HTTP_ENABLED", trueValue);
      var config = new McpServerLaunchConfiguration(environment);
      assertThat(config.isHttpEnabled()).isTrue();
    }
    
    // Test false values
    for (var falseValue : new String[]{"false", "False", "FALSE", "anything-else", ""}) {
      environment.put("SONARQUBE_HTTP_ENABLED", falseValue);
      var config = new McpServerLaunchConfiguration(environment);
      assertThat(config.isHttpEnabled()).isFalse();
    }
  }

  private Map<String, String> createMinimalTestEnvironment() {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", "https://test-sonarqube-server.com");
    return environment;
  }

}
