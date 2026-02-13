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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
  private ListAppender<ILoggingEvent> logAppender;
  private Logger mcpClientManagerLogger;

  @BeforeAll
  static void setupTestEnvironment() {
    var resourceUrl = ProxiedToolsLoaderMediumTest.class.getResource("/test-mcp-server.py");
    if (resourceUrl != null) {
      testServerScript = Paths.get(resourceUrl.getPath());
    }
  }

  @BeforeEach
  void setupLogCapture() {
    // Set up log capture for McpLogger to test log level parsing
    mcpClientManagerLogger = (Logger) LoggerFactory.getLogger("org.sonarsource.sonarqube.mcp.log.McpLogger");
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpClientManagerLogger.addAppender(logAppender);
    
    // Enable TRACE level to capture all log levels including TRACE
    mcpClientManagerLogger.setLevel(Level.TRACE);
  }

  @AfterEach
  void cleanup() throws IOException {
    if (loader != null) {
      loader.shutdown();
      loader = null;
    }
    if (testConfigFile != null && Files.exists(testConfigFile)) {
      Files.deleteIfExists(testConfigFile);
    }
    if (mcpClientManagerLogger != null && logAppender != null) {
      mcpClientManagerLogger.detachAppender(logAppender);
      logAppender.stop();
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
    assertThat(toolNames).containsExactlyInAnyOrder("test_tool_1", "test_tool_2");
  }

  @Test
  void loadProxiedTools_should_not_apply_namespace_prefix_to_tool_names() {
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
      .filter(t -> t.definition().name().equals("test_tool_1"))
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
        "supportedTransports", Set.of("stdio"),
        "instructions", "Server's instructions"
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_tool_1"))
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

    // Note: Without namespace prefixing, we'll only get 2 tools since both servers expose the same tool names
    // The second server's tools will overwrite the first server's tools in the map
    assertThat(tools).hasSize(2);

    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames).containsExactlyInAnyOrder("test_tool_1", "test_tool_2");
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
    var toolNames = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames).containsExactlyInAnyOrder("test_tool_1", "test_tool_2");
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
        "supportedTransports", Set.of("stdio"),
        "instructions", "Server's instructions"
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    // The test server includes the TEST_ENV_VAR in the tool description
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_tool_1"))
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
      .filter(t -> t.definition().name().equals("test_tool_1"))
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
      .filter(t -> t.definition().name().equals("test_tool_2"))
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
        "supportedTransports", Set.of("stdio"),
        "instructions", "Server's instructions"
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

  @Test
  void should_parse_TRACE_level_from_proxied_server_logs() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "| TRACE Test trace message");
    
    var traceLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.TRACE)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(traceLog).isPresent();
    assertThat(traceLog.get().getFormattedMessage()).contains("| TRACE Test trace message");
  }

  @Test
  void should_parse_DEBUG_level_from_proxied_server_logs() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "| DEBUG Test debug message");
    
    var debugLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.DEBUG)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(debugLog).isPresent();
    assertThat(debugLog.get().getFormattedMessage()).contains("| DEBUG Test debug message");
  }

  @Test
  void should_parse_INFO_level_from_proxied_server_logs() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "| INFO Test info message");
    
    var infoLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.INFO)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(infoLog).isPresent();
    assertThat(infoLog.get().getFormattedMessage()).contains("| INFO Test info message");
  }

  @Test
  void should_parse_WARN_level_from_proxied_server_logs() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "| WARN Test warning message");
    
    var warnLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.WARN)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(warnLog).isPresent();
    assertThat(warnLog.get().getFormattedMessage()).contains("| WARN Test warning message");
  }

  @Test
  void should_parse_ERROR_level_from_proxied_server_logs() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "| ERROR Test error message");
    
    var errorLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.ERROR)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(errorLog).isPresent();
    assertThat(errorLog.get().getFormattedMessage()).contains("| ERROR Test error message");
  }

  @Test
  void should_default_to_INFO_for_unrecognized_log_format() {
    logAppender.list.clear();
    
    McpClientManager.logProxiedServerOutput("test-server", "Some random log without level");
    
    var infoLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.INFO)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();
    
    assertThat(infoLog).isPresent();
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
