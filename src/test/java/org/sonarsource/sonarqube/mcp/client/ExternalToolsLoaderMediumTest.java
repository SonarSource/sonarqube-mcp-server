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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.external.ExternalMcpTool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ExternalToolsLoader that use a real test MCP server.
 * These tests require Python 3.
 */
class ExternalToolsLoaderMediumTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static Path testConfigFile;
  private static Path testServerScript;
  
  private ExternalToolsLoader loader;

  @BeforeAll
  static void setupTestEnvironment() {
    var resourceUrl = ExternalToolsLoaderMediumTest.class.getResource("/test-mcp-server.py");
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
  void loadExternalTools_should_discover_and_load_tools_from_test_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools)
      .isNotEmpty()
      .hasSize(2);

    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames).containsExactlyInAnyOrder("test_test_tool_1", "test_test_tool_2");
  }

  @Test
  void loadExternalTools_should_apply_namespace_prefix_to_tool_names() {
    createTestConfig(List.of(
      Map.of(
        "name", "my-provider",
        "namespace", "myprovider",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools).hasSize(2);
    
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("myprovider_test_tool_1"))
      .findFirst();
    
    assertThat(tool1).isPresent();
    assertThat(tool1.get().definition().title()).isEqualTo("Test Tool 1");
  }

  @Test
  void loadExternalTools_should_create_external_tools_with_correct_category() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools)
      .isNotEmpty()
      .allMatch(t -> t.getCategory() == ToolCategory.EXTERNAL)
      .allMatch(ExternalMcpTool.class::isInstance);
  }

  @Test
  void loadExternalTools_should_preserve_tool_metadata() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_test_tool_1"))
      .findFirst()
      .orElseThrow();

    var definition = tool1.definition();
    assertThat(definition.title()).isEqualTo("Test Tool 1");
    assertThat(definition.description()).contains("A test tool");
    assertThat(definition.inputSchema()).isNotNull();
    assertThat(definition.inputSchema().type()).isEqualTo("object");
  }

  @Test
  void loadExternalTools_should_handle_multiple_providers() {
    createTestConfig(List.of(
      Map.of(
        "name", "provider1",
        "namespace", "p1",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      ),
      Map.of(
        "name", "provider2",
        "namespace", "p2",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools).hasSize(4);

    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames)
      .contains("p1_test_tool_1", "p1_test_tool_2")
      .contains("p2_test_tool_1", "p2_test_tool_2");
  }

  @Test
  void loadExternalTools_should_gracefully_handle_failed_provider() {
    createTestConfig(List.of(
      Map.of(
        "name", "good-provider",
        "namespace", "good",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      ),
      Map.of(
        "name", "bad-provider",
        "namespace", "bad",
        "command", "/non/existent/command",
        "args", List.of(),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools).hasSize(2);
    assertThat(tools.stream().map(t -> t.definition().name())).allMatch(name -> name.startsWith("good_"));
  }

  @Test
  void loadExternalTools_should_pass_config_env_to_external_provider() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of("TEST_ENV_VAR", "config_value")
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    // The test server includes the TEST_ENV_VAR in the tool description
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_test_tool_1"))
      .findFirst()
      .orElseThrow();

    // The description should contain the config value
    assertThat(tool1.definition().description()).contains("config_value");
  }

  @Test
  void external_tools_should_be_executable() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    var tool1 = (ExternalMcpTool) tools.stream()
      .filter(t -> t.definition().name().equals("test_test_tool_1"))
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
  void external_tools_should_handle_required_parameters() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "namespace", "test",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    var tool2 = (ExternalMcpTool) tools.stream()
      .filter(t -> t.definition().name().equals("test_test_tool_2"))
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
  void loadExternalTools_should_return_empty_when_all_providers_fail() {
    createTestConfig(List.of(
      Map.of(
        "name", "bad-provider-1",
        "namespace", "bad1",
        "command", "/non/existent/command1",
        "args", List.of(),
        "env", Map.of()
      ),
      Map.of(
        "name", "bad-provider-2",
        "namespace", "bad2",
        "command", "/non/existent/command2",
        "args", List.of(),
        "env", Map.of()
      )
    ));

    loader = new ExternalToolsLoader();
    var tools = loader.loadExternalTools(TransportMode.STDIO);

    assertThat(tools).isEmpty();
  }

  private void createTestConfig(List<Map<String, Object>> configs) {
    try {
      // Create a temporary config file
      testConfigFile = Files.createTempFile("external-tools-test-", ".json");
      
      // Ensure all configs have supportedTransports if not already set
      var configsWithTransports = configs.stream()
        .map(config -> {
          if (!config.containsKey("supportedTransports")) {
            var mutableConfig = new java.util.HashMap<>(config);
            mutableConfig.put("supportedTransports", List.of("stdio"));
            return mutableConfig;
          }
          return config;
        })
        .toList();
      
      OBJECT_MAPPER.writeValue(testConfigFile.toFile(), configsWithTransports);
      System.setProperty("external.tools.config.path", testConfigFile.toString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create test configuration", e);
    }
  }

}
