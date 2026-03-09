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
    mcpClientManagerLogger = (Logger) LoggerFactory.getLogger("org.sonarsource.sonarqube.mcp.log.McpLogger");
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpClientManagerLogger.addAppender(logAppender);
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
    // Clear the system property to prevent it from affecting other tests
    System.clearProperty("proxied.mcp.servers.config.path");
  }

  @Test
  void loadProxiedTools_should_discover_and_load_tools_from_test_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
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
  void loadProxiedTools_should_create_proxied_tools_with_correct_category() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
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
      .allMatch(t -> t.getCategory() == ToolCategory.CAG)
      .allMatch(ProxiedMcpTool.class::isInstance);
  }

  @Test
  void loadProxiedTools_should_preserve_tool_metadata() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
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
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      ),
      Map.of(
        "name", "server2",
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
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      ),
      Map.of(
        "name", "bad-server",
        "command", "/non/existent/command",
        "args", List.of(),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    assertThat(tools).hasSize(2);
    var toolNames2 = tools.stream().map(t -> t.definition().name()).toList();
    assertThat(toolNames2).containsExactlyInAnyOrder("test_tool_1", "test_tool_2");
  }

  @Test
  void loadProxiedTools_should_pass_config_env_to_proxied_server() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
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
  void loadProxiedTools_should_inherit_parent_env_when_config_value_is_empty() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        // Empty string value should trigger inheritance from parent
        // The PATH environment variable should exist in parent process
        "env", Map.of("TEST_ENV_VAR", "", "PATH", ""),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    // Server should load successfully even with empty env values
    // If PATH wasn't inherited, python3 command wouldn't be found
    assertThat(tools).isNotEmpty();
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_tool_1"))
      .findFirst()
      .orElseThrow();

    assertThat(tool1.definition().name()).isEqualTo("test_tool_1");
  }

  @Test
  void loadProxiedTools_should_use_explicit_value_over_parent_env() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
        "command", "python3",
        "args", List.of(testServerScript.toString()),
        // Explicit value should be used, not inherited
        "env", Map.of("TEST_ENV_VAR", "explicit_value"),
        "supportedTransports", Set.of("stdio")
      )
    ));

    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO);

    // The test server includes TEST_ENV_VAR in the tool description
    var tool1 = tools.stream()
      .filter(t -> t.definition().name().equals("test_tool_1"))
      .findFirst()
      .orElseThrow();

    assertThat(tool1.definition().description()).contains("explicit_value");
  }

  @Test
  void proxied_tools_should_be_executable() {
    createTestConfig(List.of(
      Map.of(
        "name", "test-server",
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
        "command", "/non/existent/command1",
        "args", List.of(),
        "env", Map.of(),
        "supportedTransports", Set.of("stdio"),
        "instructions", "Server's instructions"
      ),
      Map.of(
        "name", "bad-server-2",
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
  void should_log_DEBUG_notifications_at_debug_level() {
    try {
      System.setProperty("SONARQUBE_DEBUG_ENABLED", "true");
      logAppender.list.clear();

      McpClientManager.handleProxiedServerLog("test-server",
        new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.DEBUG, null, "Test debug message", null));

      var debugLog = logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.DEBUG)
        .filter(event -> event.getFormattedMessage().contains("[test-server]"))
        .findFirst();

      assertThat(debugLog).isPresent();
      assertThat(debugLog.get().getFormattedMessage()).contains("Test debug message");
    } finally {
      System.clearProperty("SONARQUBE_DEBUG_ENABLED");
    }
  }

  @Test
  void should_log_INFO_notifications_at_info_level() {
    logAppender.list.clear();

    McpClientManager.handleProxiedServerLog("test-server",
      new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.INFO, null, "Test info message", null));

    var infoLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.INFO)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();

    assertThat(infoLog).isPresent();
    assertThat(infoLog.get().getFormattedMessage()).contains("Test info message");
  }

  @Test
  void should_log_NOTICE_notifications_at_info_level() {
    logAppender.list.clear();

    McpClientManager.handleProxiedServerLog("test-server",
      new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.NOTICE, null, "Test notice message", null));

    var infoLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.INFO)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();

    assertThat(infoLog).isPresent();
    assertThat(infoLog.get().getFormattedMessage()).contains("Test notice message");
  }

  @Test
  void should_log_WARNING_notifications_at_warn_level() {
    logAppender.list.clear();

    McpClientManager.handleProxiedServerLog("test-server",
      new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.WARNING, null, "Test warning message", null));

    var warnLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.WARN)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();

    assertThat(warnLog).isPresent();
    assertThat(warnLog.get().getFormattedMessage()).contains("Test warning message");
  }

  @Test
  void should_log_ERROR_notifications_at_error_level() {
    logAppender.list.clear();

    McpClientManager.handleProxiedServerLog("test-server",
      new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.ERROR, null, "Test error message", null));

    var errorLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.ERROR)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();

    assertThat(errorLog).isPresent();
    assertThat(errorLog.get().getFormattedMessage()).contains("Test error message");
  }

  @Test
  void should_include_logger_name_in_log_message_when_present() {
    logAppender.list.clear();

    McpClientManager.handleProxiedServerLog("test-server",
      new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.INFO, "my-logger", "Test message", null));

    var infoLog = logAppender.list.stream()
      .filter(event -> event.getLevel() == Level.INFO)
      .filter(event -> event.getFormattedMessage().contains("[test-server]"))
      .findFirst();

    assertThat(infoLog).isPresent();
    assertThat(infoLog.get().getFormattedMessage()).contains("my-logger").contains("Test message");
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
