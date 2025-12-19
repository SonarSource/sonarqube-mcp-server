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
      Map.<String, Object>of("name", "server1", "command", "/usr/bin/server"),
      Map.of("name", "server2", "command", "/usr/bin/other", "args", List.of("--port", "8080"))
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
  void validateConfig_should_fail_when_name_is_blank() {
    var config = List.of(
      Map.<String, Object>of("name", "  ", "command", "/usr/bin/server")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Missing required field 'name'");
  }

  @Test
  void validateConfig_should_fail_when_command_is_missing() {
    var config = List.of(
      Map.<String, Object>of("name", "server1")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Missing required field 'command'");
  }

  @Test
  void validateConfig_should_fail_on_duplicate_names() {
    var config = List.of(
      Map.<String, Object>of("name", "server", "command", "cmd1"),
      Map.<String, Object>of("name", "server", "command", "cmd2")
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
  void validateConfig_should_use_name_as_namespace_for_uniqueness_check() {
    // server1 uses name as namespace, server2 explicitly sets same namespace
    var config = List.of(
      Map.<String, Object>of("name", "server1", "command", "cmd1"),
      Map.<String, Object>of("name", "server2", "namespace", "server1", "command", "cmd2")
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[1]: Duplicate namespace 'server1'");
  }

  @Test
  void validateConfig_should_fail_when_args_is_not_array() {
    var configs = List.of(
      Map.<String, Object>of("name", "server", "command", "cmd", "args", "not-an-array")
    );
    
    var result = UnifiedConfigValidator.validateConfig(configs);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Field 'args' must be an array");
  }

  @Test
  void validateConfig_should_fail_when_env_is_not_object() {
    var configs = List.of(
      Map.<String, Object>of("name", "server", "command", "cmd", "env", "not-an-object")
    );
    
    var result = UnifiedConfigValidator.validateConfig(configs);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("Config[0]: Field 'env' must be an object");
  }

  @Test
  void validateConfig_should_collect_multiple_errors() {
    var config = List.of(
      Map.<String, Object>of("command", "cmd1"),  // Missing name
      Map.<String, Object>of("name", "server")    // Missing command
    );
    
    var result = UnifiedConfigValidator.validateConfig(config);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(2);
    assertThat(result.message()).isEqualTo("Found 2 validation errors");
  }

  @Test
  void validationResult_success_should_have_empty_errors() {
    var result = UnifiedConfigValidator.ValidationResult.success("OK");
    
    assertThat(result.isValid()).isTrue();
    assertThat(result.message()).isEqualTo("OK");
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void validationResult_single_failure_should_set_message_and_errors() {
    var result = UnifiedConfigValidator.ValidationResult.failure("Something went wrong");
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.message()).isEqualTo("Something went wrong");
    assertThat(result.errors()).containsExactly("Something went wrong");
  }

  @Test
  void validationResult_multiple_failure_should_summarize_message() {
    var errors = List.of("Error 1", "Error 2", "Error 3");
    var result = UnifiedConfigValidator.ValidationResult.failure(errors);
    
    assertThat(result.isValid()).isFalse();
    assertThat(result.message()).isEqualTo("Found 3 validation errors");
    assertThat(result.errors()).containsExactlyElementsOf(errors);
  }

}
