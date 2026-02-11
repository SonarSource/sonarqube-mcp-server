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

import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    Integer.parseInt(System.getProperty("mcp.client.timeout.seconds", "60")));
  
  private final List<ProxiedMcpServerConfig> serverConfigs;
  private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
  private final Map<String, List<McpSchema.Tool>> serverTools = new ConcurrentHashMap<>();
  private final Map<String, String> serverErrors = new ConcurrentHashMap<>();
  private volatile boolean initialized = false;
  
  public McpClientManager(List<ProxiedMcpServerConfig> serverConfigs) {
    this.serverConfigs = List.copyOf(serverConfigs);
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
  
  /**
   * Logs output from a proxied server, attempting to preserve the original log level.
   * Parses common log level patterns from the stderr line and routes to appropriate log level.
   */
  @VisibleForTesting
  static void logProxiedServerOutput(String serverName, String stderrLine) {
    var prefixedLine = "[" + serverName + "] " + stderrLine;

    var upperLine = stderrLine.toUpperCase(Locale.getDefault());
    
    if (upperLine.contains("| DEBUG")) {
      LOG.debug(prefixedLine);
    } else if (upperLine.contains("| ERROR")) {
      LOG.error(prefixedLine);
    } else if (upperLine.contains("| WARN")) {
      LOG.warn(prefixedLine);
    } else if (upperLine.contains("| INFO")) {
      LOG.info(prefixedLine);
    } else {
      // Default to INFO for unrecognized formats
      LOG.info(prefixedLine);
    }
  }
  
  private void initializeClient(ProxiedMcpServerConfig config) {
    var serverName = config.name();
    try {
      LOG.info("Connecting to '" + config.name() + "'");
      
      // Merge parent process environment with config-specific environment
      // Config environment takes precedence over parent environment
      var parentEnv = System.getenv();
      var mergedEnv = new HashMap<>(parentEnv);
      if (!config.env().isEmpty()) {
        LOG.debug("Merging " + config.env().size() + " config environment variable(s) for '" + config.name() + "'");
        mergedEnv.putAll(config.env());
      }

      var serverParams = ServerParameters.builder(config.command())
        .args(config.args())
        .env(mergedEnv)
        .build();
      
      var transport = new ManagedStdioClientTransport(config.name(), serverParams, McpJsonMappers.DEFAULT);
      transport.setStdErrorHandler(stderrLine -> logProxiedServerOutput(config.name(), stderrLine));

      var client = McpClient.sync(transport)
        .initializationTimeout(Duration.ofMinutes(1))
        .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
        .capabilities(McpSchema.ClientCapabilities.builder()
          .roots(false)
          .elicitation()
          .sampling()
          .build())
        .build();
      
      client.initialize();
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
      var namespace = config.namespace();
      var tools = serverTools.getOrDefault(serverId, List.of());
      tools.forEach(tool -> {
        try {
          ToolNameValidator.validate(tool.name());
          allTools.put(tool.name(), new ToolMapping(serverId, namespace, tool.name(), tool));
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

  public McpSchema.CallToolResult executeTool(String serverId, String toolName, Map<String, Object> arguments) throws IllegalStateException {
    var client = clients.get(serverId);
    if (client == null) {
      var errorMsg = serverErrors.get(serverId);
      throw new IllegalStateException(errorMsg != null ? ("Service unavailable: " + errorMsg) : "Service connection not established");
    }
    
    LOG.debug("Executing tool: " + toolName);

    var request = new McpSchema.CallToolRequest(toolName, arguments);
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

  public record ToolMapping(String serverId, String namespace, String originalToolName, McpSchema.Tool tool) {}

}
