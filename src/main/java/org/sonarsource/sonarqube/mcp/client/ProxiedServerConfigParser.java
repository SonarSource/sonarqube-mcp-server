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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class ProxiedServerConfigParser {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String DEFAULT_CONFIG_RESOURCE = "/proxied-mcp-servers.json";
  /**
   * Matches {@code ${VAR}} and {@code ${VAR:-default}} substitutions.
   * The default value (after {@code :-}) is substituted when the env var is absent or blank.
   */
  private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?}");

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
    String json;
    String source;
    
    var testConfigPath = System.getProperty("proxied.mcp.servers.config.path");
    if (testConfigPath != null) {
      try {
        json = new String(Files.readAllBytes(Paths.get(testConfigPath)));
        source = "file: " + testConfigPath;
      } catch (IOException e) {
        LOG.error("Failed to load proxied MCP servers configuration from file: " + e.getMessage(), e);
        return ParseResult.failure("Failed to load configuration from file: " + e.getMessage());
      }
    } else {
      try (var inputStream = ProxiedServerConfigParser.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
        if (inputStream == null) {
          LOG.info("No proxied MCP servers configuration found at " + DEFAULT_CONFIG_RESOURCE);
          return ParseResult.success(Collections.emptyList());
        }
        json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        source = "bundled resource: " + DEFAULT_CONFIG_RESOURCE;
      } catch (IOException e) {
        LOG.error("Failed to load proxied MCP servers configuration: " + e.getMessage(), e);
        return ParseResult.failure("Failed to load bundled configuration: " + e.getMessage());
      }
    }

    LOG.info("Loading proxied MCP servers configuration from " + source);
    return parseAndValidateProxiedConfig(json);
  }

  static ParseResult parseAndValidateProxiedConfig(String json) {
    return parseAndValidateProxiedConfig(json, System::getenv);
  }

  static ParseResult parseAndValidateProxiedConfig(String json, Function<String, String> envResolver) {
    try {
      var jsonConfigs = OBJECT_MAPPER.readValue(json, new TypeReference<List<JsonServerConfig>>() {});

      var configs = jsonConfigs.stream()
        .map(config -> toProxiedMcpServerConfig(config, envResolver))
        .toList();

      LOG.info("Successfully loaded " + configs.size() + " proxied MCP server(s)");
      return ParseResult.success(configs);
    } catch (Exception e) {
      var error = "Failed to parse configuration: " + e.getMessage();
      LOG.error(error, e);
      return ParseResult.failure(error);
    }
  }

  private static ProxiedMcpServerConfig toProxiedMcpServerConfig(JsonServerConfig jsonConfig, Function<String, String> envResolver) {
    var args = jsonConfig.args != null ? jsonConfig.args : Collections.<String>emptyList();
    return new ProxiedMcpServerConfig(
      jsonConfig.name,
      substituteEnvVars(jsonConfig.command, envResolver),
      args.stream().map(arg -> substituteEnvVars(arg, envResolver)).toList(),
      jsonConfig.env != null ? jsonConfig.env : Collections.emptyMap(),
      jsonConfig.inherits != null ? jsonConfig.inherits : Collections.emptyList(),
      jsonConfig.supportedTransports,
      jsonConfig.instructions
    );
  }

  /**
   * Expands {@code ${VAR}} and {@code ${VAR:-default}} placeholders in the input string
   * using the supplied resolver. If the env var is unset or blank and no default is provided,
   * the placeholder is replaced with the empty string.
   */
  static String substituteEnvVars(String input, Function<String, String> envResolver) {
    if (input == null || input.indexOf('$') < 0) {
      return input;
    }
    var matcher = ENV_VAR_PATTERN.matcher(input);
    var result = new StringBuilder();
    while (matcher.find()) {
      var varName = matcher.group(1);
      var defaultValue = matcher.group(2);
      var resolved = envResolver.apply(varName);
      String replacement;
      if (resolved != null && !resolved.isBlank()) {
        replacement = resolved;
      } else if (defaultValue != null) {
        replacement = defaultValue;
      } else {
        replacement = "";
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * JSON DTO record for deserializing server configuration from JSON.
   * Jackson will use this to parse the JSON directly into a type-safe record.
   * Unknown properties are ignored to allow for forward compatibility.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record JsonServerConfig(
    @JsonProperty("name") String name,
    @JsonProperty("command") String command,
    @JsonProperty("args") @Nullable List<String> args,
    @JsonProperty("env") @Nullable Map<String, String> env,
    @JsonProperty("inherits") @Nullable List<String> inherits,
    @JsonProperty("supportedTransports") Set<TransportMode> supportedTransports,
    @JsonProperty("instructions") @Nullable String instructions
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
