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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class ProxiedServerConfigParser {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String DEFAULT_CONFIG_RESOURCE = "/proxied-mcp-servers.json";
  
  private ProxiedServerConfigParser() {
    // Utility class
  }

  /**
   * Parse proxied MCP server configuration from the bundled configuration file.
   * The configuration file must be located at /proxied-mcp-servers.json in the classpath (bundled in JAR).
   * <p>
   * For testing purposes, a system property "proxied.mcp.servers.config.path" can be set to override the default location.
   */
  public static ParseResult parse() {
    // Check for test config override
    var testConfigPath = System.getProperty("proxied.mcp.servers.config.path");
    if (testConfigPath != null) {
      return parseFromFile(testConfigPath);
    }
    
    try (var inputStream = ProxiedServerConfigParser.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        LOG.info("No proxied MCP servers configuration found at " + DEFAULT_CONFIG_RESOURCE);
        return ParseResult.success(Collections.emptyList());
      }
      
      LOG.info("Loading proxied MCP servers configuration from bundled resource: " + DEFAULT_CONFIG_RESOURCE);
      var json = new String(inputStream.readAllBytes());
      return parseAndValidateProxiedConfig(json);
    } catch (IOException e) {
      LOG.error("Failed to load proxied MCP servers configuration: " + e.getMessage(), e);
      return ParseResult.failure("Failed to load bundled configuration: " + e.getMessage());
    }
  }

  static ParseResult parseAndValidateProxiedConfig(String json) {
    try {
      var jsonConfigs = OBJECT_MAPPER.readValue(json, new TypeReference<List<JsonServerConfig>>() {});

      var configs = jsonConfigs.stream()
        .map(ProxiedServerConfigParser::toProxiedMcpServerConfig)
        .toList();

      LOG.info("Successfully loaded " + configs.size() + " proxied MCP server(s)");
      return ParseResult.success(configs);
    } catch (Exception e) {
      var error = "Failed to parse configuration: " + e.getMessage();
      LOG.error(error, e);
      return ParseResult.failure(error);
    }
  }
  
  private static ProxiedMcpServerConfig toProxiedMcpServerConfig(JsonServerConfig jsonConfig) {
    return new ProxiedMcpServerConfig(
      jsonConfig.name(),
      jsonConfig.namespace(),
      jsonConfig.command(),
      jsonConfig.args() != null ? jsonConfig.args() : Collections.emptyList(),
      jsonConfig.env() != null ? jsonConfig.env() : Collections.emptyMap()
    );
  }

  @VisibleForTesting
  static ParseResult parseFromFile(String filePath) {
    try {
      LOG.info("Loading proxied MCP servers configuration from file: " + filePath);
      var json = new String(Files.readAllBytes(Paths.get(filePath)));
      return parseAndValidateProxiedConfig(json);
    } catch (IOException e) {
      LOG.error("Failed to load proxied MCP servers configuration from file: " + e.getMessage(), e);
      return ParseResult.failure("Failed to load configuration from file: " + e.getMessage());
    }
  }

  /**
   * JSON DTO record for deserializing server configuration from JSON.
   * Jackson will use this to parse the JSON directly into a type-safe record.
   * Unknown properties are ignored to allow for forward compatibility.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record JsonServerConfig(
    @JsonProperty("name") String name,
    @JsonProperty("namespace") String namespace,
    @JsonProperty("command") String command,
    @JsonProperty("args") @Nullable List<String> args,
    @JsonProperty("env") @Nullable Map<String, String> env
  ) {}

  public record ParseResult(boolean success, List<ProxiedMcpServerConfig> configs, @Nullable String error) {
    public static ParseResult success(List<ProxiedMcpServerConfig> configs) {
      return new ParseResult(true, configs, null);
    }
    
    public static ParseResult failure(String error) {
      return new ParseResult(false, Collections.emptyList(), error);
    }
  }

}
