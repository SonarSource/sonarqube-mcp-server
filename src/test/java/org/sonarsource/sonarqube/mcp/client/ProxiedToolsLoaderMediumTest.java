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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProxiedToolsLoader that use a real test MCP server.
 * These tests require Python 3.
 */
class ProxiedToolsLoaderMediumTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static Path testConfigFile;
  private static Path testServerScript;
  
  private ProxiedToolsLoader loader;

  @BeforeAll
  static void setupTestEnvironment() {
    var resourceUrl = ProxiedToolsLoaderMediumTest.class.getResource("/test-mcp-server.py");
    if (resourceUrl != null) {
      testServerScript = Paths.get(resourceUrl.getPath());
    }
  }

  @AfterEach
  void cleanup() throws IOException {
    if (loader != null) {
      loader.shutdown();
    }
    if (testConfigFile != null && Files.exists(testConfigFile)) {
      Files.deleteIfExists(testConfigFile);
    }
  }

  @Test
  void loadProxiedTools_should_discover_and_load_tools_from_test_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools)
      .isNotEmpty()
      .hasSize(2);

    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames).containsExactlyInAnyOrder("test/test_tool_1", "test/test_tool_2");
  }

  @Test
  void loadProxiedTools_should_apply_namespace_prefix_to_tool_names() {
    createTestConfig(List.of(
      Map.of(
        "name", "my-server",
        "namespace", "myserver",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools).hasSize(2);
    
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("myserver/test_tool_1"))
      .findFirst();
    
    assertThat(tool1).isPresent();
    assertThat(tool1.get().definition().title()).isEqualTo("Test Tool 1");
  }

  @Test
  void loadProxiedTools_should_create_proxied_tools_with_correct_category() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools)
      .isNotEmpty()
      .allMatch(t -> t.getCategory() == ToolCategory.EXTERNAL)
      .allMatch(ProxiedMcpTool.class::isInstance);
  }

  @Test
  void loadProxiedTools_should_preserve_tool_metadata() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test/test_tool_1"))
      .findFirst()
      .orElseThrow();

    var definition = tool1.definition();
    assertThat(definition.title()).isEqualTo("Test Tool 1");
    assertThat(definition.description()).contains("A test tool");
    assertThat(definition.inputSchema()).isNotNull();
    assertThat(definition.inputSchema().type()).isEqualTo("object");
  }

  @Test
  void loadProxiedTools_should_handle_multiple_servers() {
    createTestConfig(List.of(
      Map.of(
        "name", "server1",
        "namespace", "s1",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      ),
      Map.of(
        "name", "server2",
        "namespace", "s2",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools).hasSize(4);

    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames)
      .contains("s1/test_tool_1", "s1/test_tool_2")
      .contains("s2/test_tool_1", "s2/test_tool_2");
  }

  @Test
  void loadProxiedTools_should_gracefully_handle_failed_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "good-server",
        "namespace", "good",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      ),
      Map.of(
        "name", "bad-server",
        "namespace", "bad",
        "command", "/non/existent/command",
        "args", List.of(),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools).hasSize(2);
    assertThat(tools.stream().map(t -> t.definition().name())).allMatch(name -> name.startsWith("good/"));
  }

  @Test
  void loadProxiedTools_should_pass_config_env_to_proxied_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of("TEST_ENV_VAR", "config_value"),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    // The test server includes the TEST_ENV_VAR in the tool description
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test/test_tool_1"))
      .findFirst()
      .orElseThrow();

    // The description should contain the config value
    assertThat(tool1.definition().description()).contains("config_value");
  }

  @Test
  void proxied_tools_should_be_executable() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    var tool1 = (ProxiedMcpTool) tools.stream()
      .filter(t -> t.definition().name().equals("test/test_tool_1"))
      .findFirst()
      .orElseThrow();

    var arguments = new Tool.Arguments(Map.of("input", "test_input"));
    var result = tool1.execute(arguments);

    assertThat(result.isError()).isFalse();
    
    var callToolResult = result.toCallToolResult();
    assertThat(callToolResult.content()).isNotEmpty();
    
    var textContent = (McpSchema.TextContent) callToolResult.content().getFirst();
    assertThat(textContent.text()).contains("Test Tool 1 executed");
    assertThat(textContent.text()).contains("test_input");
  }

  @Test
  void proxied_tools_should_handle_required_parameters() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    var tool2 = (ProxiedMcpTool) tools.stream()
      .filter(t -> t.definition().name().equals("test/test_tool_2"))
      .findFirst()
      .orElseThrow();

    var arguments = new Tool.Arguments(Map.of("value", 42));
    var result = tool2.execute(arguments);

    assertThat(result.isError()).isFalse();
    
    var callToolResult = result.toCallToolResult();
    var textContent = (McpSchema.TextContent) callToolResult.content().getFirst();
    assertThat(textContent.text()).contains("Test Tool 2 executed");
    assertThat(textContent.text()).contains("42");
  }

  @Test
  void loadProxiedTools_should_return_empty_when_all_servers_fail() {
    createTestConfig(List.of(
      Map.of(
        "name", "bad-server-1",
        "namespace", "bad1",
        "command", "/non/existent/command1",
        "args", List.of(),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      ),
      Map.of(
        "name", "bad-server-2",
        "namespace", "bad2",
        "command", "/non/existent/command2",
        "args", List.of(),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools).isEmpty();
  }

  private void createTestConfig(List<Map<String, Object>> configs) {
    try {
      // Create a temporary config file
      testConfigFile = Files.createTempFile("proxied-mcp-servers-test-", ".json");
      OBJECT_MAPPER.writeValue(testConfigFile.toFile(), configs);
      System.setProperty("proxied.mcp.servers.config.path", testConfigFile.toString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create test configuration", e);
    }
  }

}
