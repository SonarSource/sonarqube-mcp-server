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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxiedServerConfigParserTest {
  
  @Test
  void parseServerConfig_should_parse_config_with_all_fields() {
    var configMap = Map.<String, Object>of(
      "name", "test-server",
      "namespace", "test",
      "command", "npx",
      "args", List.of("arg1", "arg2"),
      "env", Map.of("KEY1", "value1", "KEY2", "value2")
    );

    var config = ProxiedServerConfigParser.parseServerConfig(configMap);

    assertThat(config.name()).isEqualTo("test-server");
    assertThat(config.namespace()).isEqualTo("test");
    assertThat(config.command()).isEqualTo("npx");
    assertThat(config.args()).containsExactly("arg1", "arg2");
    assertThat(config.env()).containsEntry("KEY1", "value1").containsEntry("KEY2", "value2");
  }

  @Test
  void parseServerConfig_should_parse_config_with_only_required_fields() {
    var configMap = Map.<String, Object>of(
      "name", "minimal-server",
      "namespace", "minimal",
      "command", "node"
    );

    var config = ProxiedServerConfigParser.parseServerConfig(configMap);

    assertThat(config.name()).isEqualTo("minimal-server");
    assertThat(config.namespace()).isEqualTo("minimal");
    assertThat(config.command()).isEqualTo("node");
    assertThat(config.args()).isEmpty();
    assertThat(config.env()).isEmpty();
  }

  @Test
  void parseServerConfig_should_throw_when_name_is_blank() {
    var configMap = Map.<String, Object>of(
      "name", "  ",
      "namespace", "ns",
      "command", "node"
    );

    assertThatThrownBy(() -> ProxiedServerConfigParser.parseServerConfig(configMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server name cannot be null or blank");
  }

  @Test
  void parseServerConfig_should_throw_when_namespace_is_blank() {
    var configMap = Map.<String, Object>of(
      "name", "server",
      "namespace", "  ",
      "command", "node"
    );

    assertThatThrownBy(() -> ProxiedServerConfigParser.parseServerConfig(configMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server namespace cannot be null or blank");
  }

  @Test
  void parseServerConfig_should_throw_when_command_is_blank() {
    var configMap = Map.<String, Object>of(
      "name", "server",
      "namespace", "ns",
      "command", "  "
    );

    assertThatThrownBy(() -> ProxiedServerConfigParser.parseServerConfig(configMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server command cannot be null or blank");
  }

  @Test
  void parseServerConfig_should_handle_empty_args_list() {
    var configMap = Map.<String, Object>of(
      "name", "server",
      "namespace", "ns",
      "command", "node",
      "args", Collections.emptyList()
    );

    var config = ProxiedServerConfigParser.parseServerConfig(configMap);

    assertThat(config.args()).isEmpty();
  }

  @Test
  void parseServerConfig_should_handle_empty_env_map() {
    var configMap = Map.<String, Object>of(
      "name", "server",
      "namespace", "ns",
      "command", "node",
      "env", Collections.emptyMap()
    );

    var config = ProxiedServerConfigParser.parseServerConfig(configMap);

    assertThat(config.env()).isEmpty();
  }
  
  @Test
  void parseAndValidateJson_should_parse_valid_proxiedConfig_with_multiple_configs() {
    var json = """
      [
        {
          "name": "server1",
          "namespace": "full",
          "command": "docker",
          "args": ["run", "-it", "image"],
          "env": {
            "VAR1": "value1",
            "VAR2": "value2",
            "VAR3": "value3"
          }
        },
        {
          "name": "server2",
          "namespace": "ns2",
          "command": "python",
          "args": ["-m", "server"]
        }
      ]
      """;

    var result = ProxiedServerConfigParser.parseAndValidateProxiedConfig(json);

    assertThat(result.success()).isTrue();
    assertThat(result.configs()).hasSize(2);
    assertThat(result.configs().get(0).name()).isEqualTo("server1");
    assertThat(result.configs().get(1).name()).isEqualTo("server2");
    assertThat(result.error()).isNull();
  }

  @Test
  void parseAndValidateJson_should_handle_empty_proxiedConfig_array() {
    var json = "[]";

    var result = ProxiedServerConfigParser.parseAndValidateProxiedConfig(json);

    assertThat(result.success()).isTrue();
    assertThat(result.configs()).isEmpty();
    assertThat(result.error()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidJsonTestCases")
  void parseAndValidateProxiedConfig_should_fail_on_invalid_input(String testName, String json, String expectedErrorSubstring) {
    var result = ProxiedServerConfigParser.parseAndValidateProxiedConfig(json);

    assertThat(result.success()).isFalse();
    assertThat(result.configs()).isEmpty();
    assertThat(result.error()).contains(expectedErrorSubstring);
  }

  private static Stream<Arguments> invalidJsonTestCases() {
    return Stream.of(
      Arguments.of(
        "invalid json syntax",
        "{ invalid json }",
        "Failed to parse configuration"
      ),
      Arguments.of(
        "missing required field name",
        """
        [
          {
            "namespace": "ns1",
            "command": "node"
          }
        ]
        """,
        "Failed to parse configuration"
      ),
      Arguments.of(
        "missing required field namespace",
        """
        [
          {
            "name": "server1",
            "command": "node"
          }
        ]
        """,
        "Failed to parse configuration"
      ),
      Arguments.of(
        "missing required field command",
        """
        [
          {
            "name": "server1",
            "namespace": "ns1"
          }
        ]
        """,
        "Failed to parse configuration"
      ),
      Arguments.of(
        "name is blank",
        """
        [
          {
            "name": "  ",
            "namespace": "ns1",
            "command": "node"
          }
        ]
        """,
        "name cannot be null or blank"
      ),
      Arguments.of(
        "namespace is blank",
        """
        [
          {
            "name": "server1",
            "namespace": "  ",
            "command": "node"
          }
        ]
        """,
        "namespace cannot be null or blank"
      ),
      Arguments.of(
        "config has invalid field type",
        """
        [
          {
            "name": "server",
            "namespace": "ns",
            "command": "node",
            "args": "should-be-array"
          }
        ]
        """,
        "Failed to parse configuration"
      )
    );
  }

  @Test
  void parseAndValidateProxiedConfig_should_handle_extra_unknown_fields() {
    var json = """
      [
        {
          "name": "server",
          "namespace": "ns",
          "command": "node",
          "extra_field": "should be ignored",
          "another_unknown": 123
        }
      ]
      """;

    var result = ProxiedServerConfigParser.parseAndValidateProxiedConfig(json);

    assertThat(result.success()).isTrue();
    assertThat(result.configs()).hasSize(1);
    assertThat(result.configs().getFirst().name()).isEqualTo("server");
  }

  @Test
  void parseAndValidateProxiedConfig_should_preserve_arg_order() {
    var json = """
      [
        {
          "name": "server",
          "namespace": "ns",
          "command": "cmd",
          "args": ["first", "second", "third", "fourth"]
        }
      ]
      """;

    var result = ProxiedServerConfigParser.parseAndValidateProxiedConfig(json);

    assertThat(result.success()).isTrue();
    assertThat(result.configs().getFirst().args()).containsExactly("first", "second", "third", "fourth");
  }

}
