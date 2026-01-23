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

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.external.ExternalMcpTool;

public class ExternalToolsLoader {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  
  @Nullable
  private McpClientManager mcpClientManager;
  
  /**
   * 1. Parses the configuration from external-tool-providers.json
   * 2. Validates the configuration
   * 3. Filters providers based on current transport mode
   * 4. Connects to each compatible external tool provider
   * 5. Discovers tools from connected providers
   * 6. Creates tool wrappers for integration
   */
  public List<Tool> loadExternalTools(TransportMode currentTransportMode) {
    var parseResult = ExternalServerConfigParser.parse();
    
    if (!parseResult.success()) {
      LOG.warn("Failed to load external tool providers configuration: " + parseResult.error());
      LOG.warn("Continuing without external tool providers");
      return List.of();
    }
    
    var allConfigs = parseResult.configs();
    if (allConfigs.isEmpty()) {
      LOG.info("No external tool providers configured");
      return List.of();
    }

    // Filter configs based on transport compatibility
    var compatibleConfigs = allConfigs.stream().filter(config -> {
        if (config.supportsTransport(currentTransportMode)) {
          return true;
        } else {
          LOG.info("Skipping external provider '" + config.name() + "' - " +
            "does not support " + currentTransportMode.toConfigString() + " transport (supports: " + config.supportedTransports() + ")");
          return false;
        }
      })
      .toList();
    
    if (compatibleConfigs.isEmpty()) {
      LOG.info("No external tool providers compatible with " + currentTransportMode.toConfigString() + " transport (total configured: " + allConfigs.size() + ")");
      return List.of();
    }
    
    LOG.info("Initializing " + compatibleConfigs.size() + " external tool provider(s) compatible with " + currentTransportMode.toConfigString() + " transport...");
    
    try {
      mcpClientManager = new McpClientManager(compatibleConfigs);
      mcpClientManager.initialize();

      var externalTools = mcpClientManager.getAllExternalTools();
      var tools = externalTools.entrySet().stream()
        .map(e -> (Tool) new ExternalMcpTool(
          e.getKey(),
          e.getValue().serverId(),
          e.getValue().originalToolName(),
          e.getValue().tool(),
          mcpClientManager
        ))
        .toList();
      
      LOG.info("Loaded " + tools.size() + " external tool(s) from " + 
        mcpClientManager.getConnectedCount() + "/" + mcpClientManager.getTotalCount() + " provider(s)");
      
      return tools;
    } catch (Exception e) {
      LOG.error("Failed to initialize external tool providers: " + e.getMessage(), e);
      LOG.warn("Continuing without external tool providers");
      return List.of();
    }
  }

  public void shutdown() {
    if (mcpClientManager != null) {
      try {
        mcpClientManager.shutdown();
      } catch (Exception e) {
        LOG.error("Error shutting down external tool providers: " + e.getMessage(), e);
      }
    }
  }

}
