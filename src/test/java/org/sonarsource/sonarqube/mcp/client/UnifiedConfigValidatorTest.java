/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedConfigValidatorTest {

  @Test
  void validateConfig_should_succeed_when_config_is_empty() {
    var result = UnifiedConfigValidator.validateConfig(Collections.emptyList());
    
    assertThat(result.isValid()).isTrue();
    assertThat(result.message()).isEqualTo("Configuration is empty but valid");
  }

  @Test
  void validateConfig_should_succeed_for_valid_config() {
    var config = List.of(
      Map.<String, Object>of("name", "server1", "namespace", "ns1", "command", "/usr/bin/server"),
      Map.of("name", "server2", "namespace", "ns2", "command", "/usr/bin/other", "args", List.of("--port", "8080"))
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isTrue();
    assertThat(result.message()).isEqualTo("Configuration is valid with 2 server(s)");
  }

  @Test
  void validateConfig_should_fail_when_name_is_missing() {
    var config = List.of(
      Map.<String, Object>of("command", "/usr/bin/server")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Missing required field 'name'");
  }

  @Test
  void validateConfig_should_fail_when_namespace_is_missing() {
    var config = List.of(
      Map.<String, Object>of("name", "server1", "command", "/usr/bin/server")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Missing required field 'namespace'");
  }

  @Test
  void validateConfig_should_fail_when_command_is_missing() {
    var config = List.of(
      Map.<String, Object>of("name", "server1", "namespace", "ns1")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Missing required field 'command'");
  }

  @Test
  void validateConfig_should_fail_on_duplicate_names() {
    var config = List.of(
      Map.<String, Object>of("name", "server", "namespace", "ns1", "command", "cmd1"),
      Map.<String, Object>of("name", "server", "namespace", "ns2", "command", "cmd2")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[1]: Duplicate name 'server'");
  }

  @Test
  void validateConfig_should_fail_on_duplicate_namespaces() {
    var config = List.of(
      Map.<String, Object>of("name", "server1", "namespace", "shared", "command", "cmd1"),
      Map.<String, Object>of("name", "server2", "namespace", "shared", "command", "cmd2")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[1]: Duplicate namespace 'shared'");
  }

  @Test
  void validateConfig_should_fail_when_args_is_not_array() {
    var configs = List.of(
      Map.<String, Object>of("name", "server", "namespace", "ns1", "command", "cmd", "args", "not-an-array")
    );
    
    var result = UnifiedConfigValidator.validateConfig(configs);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Field 'args' must be an array");
  }

  @Test
  void validateConfig_should_fail_when_env_is_not_object() {
    var configs = List.of(
      Map.<String, Object>of("name", "server", "namespace", "ns1", "command", "cmd", "env", "not-an-object")
    );

    var result = UnifiedConfigValidator.validateConfig(configs);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Field 'env' must be an object");
  }

  @Test
  void validateConfig_should_collect_multiple_errors() {
    var config = List.of(
      Map.<String, Object>of("command", "cmd1"),
      Map.<String, Object>of("name", "server")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(4);
    assertThat(result.message()).isEqualTo("Found 4 validation errors");
  }

  @Test
  void validateConfig_should_fail_with_detailed_errors_for_multiple_configs() {
    var config = List.of(
      Map.<String, Object>of("name", "server1", "command", "cmd1"),
      Map.<String, Object>of("namespace", "ns2", "command", "cmd2"),
      Map.<String, Object>of("name", "server3", "namespace", "ns3")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains(
      "Config[0]: Missing required field 'namespace'",
      "Config[1]: Missing required field 'name'",
      "Config[2]: Missing required field 'command'"
    );
  }

}
