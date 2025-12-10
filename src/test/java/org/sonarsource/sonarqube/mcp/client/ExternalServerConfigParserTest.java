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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalServerConfigParserTest {

  @TempDir
  Path tempDir;

  @Test
  void parse_should_return_empty_list_when_config_is_null() {
    var result = ExternalServerConfigParser.parse(null);
    
    assertThat(result).isEmpty();
  }

  @Test
  void parse_should_return_empty_list_when_config_is_blank() {
    var result = ExternalServerConfigParser.parse("   ");
    
    assertThat(result).isEmpty();
  }

  @Test
  void parse_should_parse_json_string_directly() {
    var json = """
      [
        {
          "name": "weather",
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-everything"],
          "env": {"API_KEY": "secret123"}
        }
      ]
      """;
    
    var result = ExternalServerConfigParser.parse(json);
    
    assertThat(result).hasSize(1);
    var config = result.get(0);
    assertThat(config.name()).isEqualTo("weather");
    assertThat(config.namespace()).isEqualTo("weather"); // Defaults to name
    assertThat(config.command()).isEqualTo("npx");
    assertThat(config.args()).containsExactly("-y", "@modelcontextprotocol/server-everything");
    assertThat(config.env()).containsEntry("API_KEY", "secret123");
  }

  @Test
  void parse_should_parse_json_with_namespace() {
    var json = """
      [
        {
          "name": "internal-caas",
          "namespace": "context",
          "command": "uv",
          "args": ["run", "server"]
        }
      ]
      """;
    
    var result = ExternalServerConfigParser.parse(json);
    
    assertThat(result).hasSize(1);
    var config = result.get(0);
    assertThat(config.name()).isEqualTo("internal-caas");
    assertThat(config.namespace()).isEqualTo("context");
  }

  @Test
  void parse_should_parse_multiple_servers() {
    var json = """
      [
        {"name": "server1", "command": "cmd1"},
        {"name": "server2", "command": "cmd2"},
        {"name": "server3", "command": "cmd3"}
      ]
      """;
    
    var result = ExternalServerConfigParser.parse(json);
    
    assertThat(result).hasSize(3);
    assertThat(result.get(0).name()).isEqualTo("server1");
    assertThat(result.get(1).name()).isEqualTo("server2");
    assertThat(result.get(2).name()).isEqualTo("server3");
  }

  @Test
  void parse_should_parse_from_file() throws IOException {
    var configFile = tempDir.resolve("config.json");
    var json = """
      [
        {
          "name": "filesystem",
          "command": "npx",
          "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
        }
      ]
      """;
    Files.writeString(configFile, json);
    
    var result = ExternalServerConfigParser.parse(configFile.toString());
    
    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("filesystem");
    assertThat(result.get(0).args()).containsExactly("-y", "@modelcontextprotocol/server-filesystem", "/tmp");
  }

  @Test
  void parse_should_return_empty_list_on_invalid_json() {
    var invalidJson = "{ this is not valid json }";
    
    var result = ExternalServerConfigParser.parse(invalidJson);
    
    assertThat(result).isEmpty();
  }

  @Test
  void parse_should_return_empty_list_when_file_does_not_exist() {
    var nonExistentPath = "/non/existent/path/config.json";
    
    // If it doesn't look like a file path, it will try to parse as JSON and fail
    var result = ExternalServerConfigParser.parse(nonExistentPath);
    
    assertThat(result).isEmpty();
  }

  @Test
  void parse_should_handle_empty_args_and_env() {
    var json = """
      [
        {
          "name": "simple",
          "command": "/usr/bin/server"
        }
      ]
      """;
    
    var result = ExternalServerConfigParser.parse(json);
    
    assertThat(result).hasSize(1);
    assertThat(result.get(0).args()).isEmpty();
    assertThat(result.get(0).env()).isEmpty();
  }

  @Test
  void parse_should_handle_empty_array() {
    var json = "[]";
    
    var result = ExternalServerConfigParser.parse(json);
    
    assertThat(result).isEmpty();
  }
}


