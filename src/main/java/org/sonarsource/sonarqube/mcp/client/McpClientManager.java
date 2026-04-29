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
package org.sonarsource.sonarqube.mcp.client;

import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.ToolNameValidator;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;

/**
 * Manages connections to proxied MCP servers.
 * This allows the SonarQube MCP server to act as a client to other MCP servers
 * and expose their tools through the SonarQube MCP server.
 */
public class McpClientManager {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(
    Integer.parseInt(System.getProperty("mcp.client.timeout.seconds", "600")));
  private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(
    Integer.parseInt(System.getProperty("mcp.client.init.timeout.seconds", "60")));
  
  private final List<ProxiedMcpServerConfig> serverConfigs;
  private final String mcpServerId;
  private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
  private final Map<String, List<McpSchema.Tool>> serverTools = new ConcurrentHashMap<>();
  private final Map<String, String> serverErrors = new ConcurrentHashMap<>();
  private volatile boolean initialized = false;

  public McpClientManager(List<ProxiedMcpServerConfig> serverConfigs, String mcpServerId) {
    this.serverConfigs = List.copyOf(serverConfigs);
    this.mcpServerId = mcpServerId;
  }

  public void initialize() {
    if (initialized) {
      return;
    }
    LOG.info("Initializing MCP client manager with " + serverConfigs.size() + " server(s)");
    serverConfigs.forEach(this::initializeClient);
    initialized = true;
    LOG.info("MCP client manager initialization completed. " + getConnectedCount() + "/" + serverConfigs.size() + " server(s) connected");
  }

  private static void setLoggingLevel(McpSyncClient client, String serverName) {
    var minLevel = McpLogger.isDebugEnabled() ? McpSchema.LoggingLevel.DEBUG : McpSchema.LoggingLevel.INFO;
    try {
      client.setLoggingLevel(minLevel);
    } catch (Exception e) {
      LOG.debug("Server '" + serverName + "' does not support logging/setLevel: " + e.getMessage());
    }
  }

  @VisibleForTesting
  static void handleProxiedServerLog(String serverName, McpSchema.LoggingMessageNotification notification) {
    var prefix = "[" + serverName + "] ";
    var message = prefix + (notification.logger() != null ? (notification.logger() + ": ") : "") + notification.data();
    switch (notification.level()) {
      case DEBUG -> LOG.debug(message);
      case INFO, NOTICE -> LOG.info(message);
      case WARNING -> LOG.warn(message);
      case ERROR, CRITICAL, ALERT, EMERGENCY -> LOG.error(message);
    }
  }

  private void initializeClient(ProxiedMcpServerConfig config) {
    var serverName = config.name();
    try {
      LOG.info("Connecting to '" + config.name() + "'");
      
      // Build server parameters for STDIO transport
      var serverParamsBuilder = ServerParameters.builder(config.command());
      if (!config.args().isEmpty()) {
        serverParamsBuilder.args(config.args());
      }
      
      // Build environment variables: explicit values from config + inherited from parent
      var filteredEnv = buildEnvironmentVariables(config, System.getenv());
      
      LOG.debug("Passing " + filteredEnv.size() + " environment variable(s) to '" + config.name() + "' (" + 
        config.env().size() + " explicit, " + config.inherits().size() + " inherited)");
      serverParamsBuilder.env(filteredEnv);

      var serverParams = serverParamsBuilder.build();
      var stdioTransport = new ManagedStdioClientTransport(config.name(), serverParams, McpJsonMappers.DEFAULT);
      stdioTransport.setStdErrorHandler(line -> LOG.debug("[" + config.name() + "] stderr: " + line));
      var transport = wrapWithInitializeMeta(stdioTransport);

      var client = McpClient.sync(transport)
        .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
        .initializationTimeout(INITIALIZATION_TIMEOUT)
        .capabilities(McpSchema.ClientCapabilities.builder()
          .roots(false)
          .elicitation()
          .sampling()
          .build())
        .loggingConsumer(notification -> handleProxiedServerLog(config.name(), notification))
        .build();

      client.initialize();
      setLoggingLevel(client, config.name());
      var listToolsResult = client.listTools();
      var tools = listToolsResult.tools();
      
      LOG.info("Connected to '" + config.name() + "' - discovered " + tools.size() + " tool(s)");
      clients.put(serverName, client);
      serverTools.put(serverName, tools);
      tools.forEach(tool -> LOG.debug(" - " + tool.name()));
    } catch (Exception e) {
      LOG.error("Failed to initialize '" + config.name() + "': " + e.getMessage(), e);
      serverErrors.put(serverName, e.getMessage());
    }
  }

  public Map<String, ToolMapping> getAllProxiedTools() {
    var allTools = new HashMap<String, ToolMapping>();
    for (var config : serverConfigs) {
      var serverId = config.name();
      var tools = serverTools.getOrDefault(serverId, List.of());
      tools.forEach(tool -> {
        try {
          ToolNameValidator.validate(tool.name());
          allTools.put(tool.name(), new ToolMapping(serverId, tool.name(), tool));
        } catch (IllegalArgumentException e) {
          LOG.error("Skipping tool with invalid name from server '" + serverId + "': " + e.getMessage(), e);
        }
      });
    }
    return allTools;
  }

  public boolean isServerConnected(String serverId) {
    return clients.containsKey(serverId);
  }

  public McpSchema.CallToolResult executeTool(String serverId, String toolName, Map<String, Object> arguments, @Nullable Map<String, Object> meta) throws IllegalStateException {
    var client = clients.get(serverId);
    if (client == null) {
      var errorMsg = serverErrors.get(serverId);
      throw new IllegalStateException(errorMsg != null ? ("Service unavailable: " + errorMsg) : "Service connection not established");
    }

    LOG.info("Executing tool: " + toolName);

    var request = new McpSchema.CallToolRequest(toolName, arguments, meta);
    return client.callTool(request);
  }

  public void shutdown() {
    LOG.info("Shutting down MCP client manager...");

    clients.forEach((key, client) -> {
      try {
        LOG.info("Closing connection: " + key);
        client.closeGracefully();
      } catch (Exception e) {
        LOG.error("Error closing client for " + key + ": " + e.getMessage(), e);
      }
    });
    
    clients.clear();
    serverTools.clear();
    serverErrors.clear();
    initialized = false;
    
    LOG.info("MCP client manager shutdown completed");
  }

  public boolean isInitialized() {
    return initialized;
  }

  public int getConnectedCount() {
    return clients.size();
  }

  public int getTotalCount() {
    return serverConfigs.size();
  }
  
  @VisibleForTesting
  Map<String, McpSyncClient> getClients() {
    return Map.copyOf(clients);
  }

  /**
   * Builds the environment variables map for a proxied server by combining explicit values from config and inherited values from parent environment.
   */
  @VisibleForTesting
  Map<String, String> buildEnvironmentVariables(ProxiedMcpServerConfig config, Map<String, String> parentEnv) {
    var filteredEnv = new HashMap<String, String>();
    
    // Add explicit values from config
    if (!config.env().isEmpty()) {
      filteredEnv.putAll(config.env());
    }
    
    // Add inherited variables from parent environment
    if (!config.inherits().isEmpty()) {
      for (var inheritKey : config.inherits()) {
        if (parentEnv.containsKey(inheritKey)) {
          // Only inherit if not already explicitly set in config
          filteredEnv.putIfAbsent(inheritKey, parentEnv.get(inheritKey));
        } else {
          LOG.debug("Cannot inherit '" + inheritKey + "' for '" + config.name() + "': variable not found in parent environment");
        }
      }
    }
    
    return filteredEnv;
  }

  private McpClientTransport wrapWithInitializeMeta(McpClientTransport transport) {
    var meta = Map.<String, Object>of("mcp_server_id", mcpServerId);
    return new InitializeMetaInjectingClientTransport(transport, meta);
  }

  public record ToolMapping(String serverId, String originalToolName, McpSchema.Tool tool) {}

}
