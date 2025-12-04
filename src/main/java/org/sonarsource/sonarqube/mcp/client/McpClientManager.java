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
import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Manages connections to external MCP servers.
 * This allows the SonarQube MCP server to act as a client to other MCP servers
 * and expose their tools through the SonarQube MCP server.
 * 
 * Features:
 * - Namespace-based tool naming (user-visible, not provider-visible)
 * - Health tracking for each provider
 * - Automatic restart with configurable backoff
 * - Graceful degradation when providers are unavailable
 */
public class McpClientManager {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
  
  private final List<ExternalMcpServerConfig> serverConfigs;
  private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
  private final Map<String, List<McpSchema.Tool>> serverTools = new ConcurrentHashMap<>();
  private final Map<String, ProviderHealth> providerHealth = new ConcurrentHashMap<>();
  private final Map<String, ExternalMcpServerConfig> configByServerId = new HashMap<>();
  private volatile boolean initialized = false;
  
  public McpClientManager(List<ExternalMcpServerConfig> serverConfigs) {
    this.serverConfigs = List.copyOf(serverConfigs);
    for (var config : serverConfigs) {
      configByServerId.put(config.getServerId(), config);
    }
  }
  
  /**
   * Initialize connections to all configured external MCP servers.
   * This method is idempotent and can be called multiple times safely.
   * 
   * @return CompletableFuture that completes when all clients are initialized
   */
  public CompletableFuture<Void> initialize() {
    if (initialized) {
      return CompletableFuture.completedFuture(null);
    }
    
    LOG.info("Initializing MCP client manager with " + serverConfigs.size() + " server(s)");
    
    return CompletableFuture.runAsync(() -> {
      for (var config : serverConfigs) {
        initializeClientWithRetry(config, 0);
      }
      initialized = true;
      LOG.info("MCP client manager initialization completed. " + getHealthySummary());
    });
  }
  
  /**
   * Initialize a client with automatic retry on failure.
   */
  private void initializeClientWithRetry(ExternalMcpServerConfig config, int attempt) {
    var serverId = config.getServerId();
    
    try {
      initializeClient(config);
      providerHealth.put(serverId, ProviderHealth.healthy());
    } catch (Exception e) {
      LOG.error("Failed to initialize '" + config.name() + "' (attempt " + (attempt + 1) + "): " + e.getMessage(), e);
      
      var restartConfig = config.restart();
      if (restartConfig.enabled() && attempt < restartConfig.maxAttempts()) {
        var backoffSeconds = restartConfig.getBackoffForAttempt(attempt);
        LOG.info("Will retry '" + config.name() + "' in " + backoffSeconds + " seconds...");
        
        try {
          TimeUnit.SECONDS.sleep(backoffSeconds);
          initializeClientWithRetry(config, attempt + 1);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          markProviderUnavailable(serverId, "Initialization interrupted", e);
        }
      } else {
        markProviderUnavailable(serverId, "Max retry attempts exceeded", e);
      }
    }
  }
  
  private void initializeClient(ExternalMcpServerConfig config) {
    var serverId = config.getServerId();
    LOG.info("Connecting to '" + config.name() + "' (namespace: " + config.getToolNamespace() + ")");
    
    // Build server parameters for STDIO transport
    var serverParamsBuilder = ServerParameters.builder(config.command());
    
    if (!config.args().isEmpty()) {
      serverParamsBuilder.args(config.args());
    }
    
    if (!config.env().isEmpty()) {
      serverParamsBuilder.env(config.env());
    }
    
    var serverParams = serverParamsBuilder.build();
    var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
    var transport = new StdioClientTransport(serverParams, jsonMapper);
    
    // Create and configure the MCP client
    var client = McpClient.sync(transport)
      .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
      .capabilities(McpSchema.ClientCapabilities.builder()
        .roots(false)
        .elicitation()
        .sampling()
        .build())
      .build();
    
    // Initialize the connection
    client.initialize();
    
    // Discover available tools from the external server
    var listToolsResult = client.listTools();
    var tools = listToolsResult.tools();
    
    LOG.info("Connected to '" + config.name() + "' - discovered " + tools.size() + " tool(s)");
    
    // Store the client and tools
    clients.put(serverId, client);
    serverTools.put(serverId, tools);
    
    // Log discovered tools (using namespace prefix for clarity)
    for (var tool : tools) {
      LOG.debug("  - " + config.getToolNamespace() + "_" + tool.name());
    }
  }
  
  private void markProviderUnavailable(String serverId, String reason, @Nullable Exception cause) {
    var config = configByServerId.get(serverId);
    var message = reason + (cause != null ? ": " + cause.getMessage() : "");
    providerHealth.put(serverId, ProviderHealth.unavailable(message));
    
    // Keep the tools registered but mark provider as unhealthy
    // Tools will still appear but will return user-friendly errors when called
    if (config != null) {
      LOG.warn("Provider '" + config.name() + "' is unavailable: " + message);
    }
  }
  
  /**
   * Get all tools from all configured servers (including unavailable ones).
   * Tools are prefixed with the namespace (not the internal server ID).
   * 
   * @return Map of prefixed tool name to tool mapping
   */
  public Map<String, ToolMapping> getAllExternalTools() {
    var allTools = new HashMap<String, ToolMapping>();
    
    for (var config : serverConfigs) {
      var serverId = config.getServerId();
      var namespace = config.getToolNamespace();
      var tools = serverTools.getOrDefault(serverId, List.of());
      
      for (var tool : tools) {
        var prefixedName = namespace + "_" + tool.name();
        allTools.put(prefixedName, new ToolMapping(serverId, namespace, tool.name(), tool));
      }
    }
    
    return allTools;
  }
  
  /**
   * Check if a provider is healthy and available.
   */
  public boolean isProviderHealthy(String serverId) {
    var health = providerHealth.get(serverId);
    return health != null && health.isHealthy();
  }
  
  /**
   * Get the health status of a provider.
   */
  @Nullable
  public ProviderHealth getProviderHealth(String serverId) {
    return providerHealth.get(serverId);
  }
  
  /**
   * Execute a tool on an external MCP server.
   * If the provider is unavailable, attempts to restart it first.
   * 
   * @param serverId The ID of the server to execute on
   * @param toolName The original tool name (without prefix)
   * @param arguments The tool arguments
   * @return The tool execution result
   * @throws ProviderUnavailableException if the provider is not available
   */
  public McpSchema.CallToolResult executeTool(String serverId, String toolName, Map<String, Object> arguments) 
      throws ProviderUnavailableException {
    
    // Check health and attempt restart if needed
    if (!isProviderHealthy(serverId)) {
      attemptRestart(serverId);
      
      // Check again after restart attempt
      if (!isProviderHealthy(serverId)) {
        var health = providerHealth.get(serverId);
        var reason = health != null ? health.reason() : "Unknown error";
        throw new ProviderUnavailableException(reason);
      }
    }
    
    var client = clients.get(serverId);
    if (client == null) {
      throw new ProviderUnavailableException("Service connection not established");
    }
    
    LOG.debug("Executing tool: " + toolName);
    
    try {
      var request = new McpSchema.CallToolRequest(toolName, arguments);
      return client.callTool(request);
    } catch (Exception e) {
      // Mark provider as unhealthy on execution failure
      LOG.error("Tool execution failed for '" + toolName + "': " + e.getMessage(), e);
      markProviderUnavailable(serverId, "Execution error", e);
      throw new ProviderUnavailableException("This feature encountered an error. Please try again later.");
    }
  }
  
  /**
   * Attempt to restart an unavailable provider.
   */
  private void attemptRestart(String serverId) {
    var config = configByServerId.get(serverId);
    if (config == null || !config.restart().enabled()) {
      return;
    }
    
    LOG.info("Attempting to restart '" + config.name() + "'...");
    
    // Close existing client if any
    var existingClient = clients.remove(serverId);
    if (existingClient != null) {
      try {
        existingClient.closeGracefully();
      } catch (Exception e) {
        LOG.debug("Error closing existing client: " + e.getMessage());
      }
    }
    
    // Try to reinitialize
    initializeClientWithRetry(config, 0);
  }
  
  /**
   * Shutdown all connected clients.
   */
  public void shutdown() {
    LOG.info("Shutting down MCP client manager...");
    
    for (var entry : clients.entrySet()) {
      try {
        LOG.info("Closing connection: " + entry.getKey());
        entry.getValue().closeGracefully();
      } catch (Exception e) {
        LOG.error("Error closing client for " + entry.getKey() + ": " + e.getMessage(), e);
      }
    }
    
    clients.clear();
    serverTools.clear();
    providerHealth.clear();
    initialized = false;
    
    LOG.info("MCP client manager shutdown completed");
  }
  
  /**
   * Check if the manager has been initialized.
   */
  public boolean isInitialized() {
    return initialized;
  }
  
  /**
   * Get the number of healthy providers.
   */
  public int getHealthyProviderCount() {
    return (int) providerHealth.values().stream().filter(ProviderHealth::isHealthy).count();
  }
  
  /**
   * Get the total number of configured providers.
   */
  public int getTotalProviderCount() {
    return serverConfigs.size();
  }
  
  private String getHealthySummary() {
    var healthy = getHealthyProviderCount();
    var total = getTotalProviderCount();
    return healthy + "/" + total + " server(s) connected";
  }
  
  @VisibleForTesting
  Map<String, McpSyncClient> getClients() {
    return Map.copyOf(clients);
  }
  
  /**
   * Represents a mapping from a prefixed tool name to its original tool and server.
   */
  public record ToolMapping(
    String serverId,
    String namespace,
    String originalToolName,
    McpSchema.Tool tool
  ) {}
  
  /**
   * Represents the health status of a provider.
   */
  public record ProviderHealth(
    boolean isHealthy,
    String reason,
    Instant timestamp
  ) {
    public static ProviderHealth healthy() {
      return new ProviderHealth(true, "Connected", Instant.now());
    }
    
    public static ProviderHealth unavailable(String reason) {
      return new ProviderHealth(false, reason, Instant.now());
    }
  }
  
  /**
   * Exception thrown when a provider is unavailable.
   * The message is user-friendly and does not expose internal details.
   */
  public static class ProviderUnavailableException extends Exception {
    public ProviderUnavailableException(String message) {
      super(message);
    }
  }
}
