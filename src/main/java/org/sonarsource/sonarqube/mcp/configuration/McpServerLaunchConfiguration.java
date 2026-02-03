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
package org.sonarsource.sonarqube.mcp.configuration;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static java.util.Objects.requireNonNull;

public class McpServerLaunchConfiguration {

  private static final String APP_NAME = "SonarQube MCP Server";

  private static final String STORAGE_PATH = "STORAGE_PATH";
  @Deprecated(forRemoval = true)
  private static final String SONARQUBE_CLOUD_URL = "SONARQUBE_CLOUD_URL";
  private static final String SONARQUBE_URL = "SONARQUBE_URL";
  private static final String SONARQUBE_ORG = "SONARQUBE_ORG";
  private static final String SONARQUBE_TOKEN = "SONARQUBE_TOKEN";
  private static final String SONARQUBE_IDE_PORT_ENV = "SONARQUBE_IDE_PORT";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";
  
  // Tool category configuration
  private static final String SONARQUBE_TOOLSETS = "SONARQUBE_TOOLSETS";
  private static final String SONARQUBE_READ_ONLY = "SONARQUBE_READ_ONLY";
  
  // HTTP/HTTPS transport configuration
  private static final String SONARQUBE_TRANSPORT = "SONARQUBE_TRANSPORT";
  private static final String SONARQUBE_HTTP_PORT = "SONARQUBE_HTTP_PORT";
  private static final String SONARQUBE_HTTP_HOST = "SONARQUBE_HTTP_HOST";
  
  // HTTPS/SSL configuration
  private static final String SONARQUBE_HTTPS_KEYSTORE_PATH = "SONARQUBE_HTTPS_KEYSTORE_PATH";
  private static final String SONARQUBE_HTTPS_KEYSTORE_PASSWORD = "SONARQUBE_HTTPS_KEYSTORE_PASSWORD";
  private static final String SONARQUBE_HTTPS_KEYSTORE_TYPE = "SONARQUBE_HTTPS_KEYSTORE_TYPE";
  private static final String SONARQUBE_HTTPS_TRUSTSTORE_PATH = "SONARQUBE_HTTPS_TRUSTSTORE_PATH";
  private static final String SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD = "SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD";
  private static final String SONARQUBE_HTTPS_TRUSTSTORE_TYPE = "SONARQUBE_HTTPS_TRUSTSTORE_TYPE";
  
  // Default values for HTTPS
  private static final String DEFAULT_KEYSTORE_PASSWORD = "sonarlint";
  private static final String DEFAULT_KEYSTORE_TYPE = "PKCS12";
  private static final String DEFAULT_KEYSTORE_PATH = "/etc/ssl/mcp/keystore.p12";
  private static final String DEFAULT_TRUSTSTORE_PATH = "/etc/ssl/mcp/truststore.p12";
  
  // HTTP authentication configuration
  private static final String SONARQUBE_HTTP_AUTH_MODE = "SONARQUBE_HTTP_AUTH_MODE";

  private final Path storagePath;
  private final String hostMachineAddress;
  private final String sonarqubeUrl;
  @Nullable
  private final String sonarqubeOrg;
  private final String sonarqubeToken;
  private final Integer sonarqubeIdePort;
  private final String appVersion;
  private final String userAgent;
  private final boolean isTelemetryEnabled;
  private final boolean isSonarCloud;
  
  // HTTP transport configuration
  private final boolean isHttpEnabled;
  private final int httpPort;
  private final String httpHost;
  // HTTPS/SSL configuration
  private final boolean isHttpsEnabled;
  private final Path httpsKeystorePath;
  private final String httpsKeystorePassword;
  private final String httpsKeystoreType;
  private final Path httpsTruststorePath;
  private final String httpsTruststorePassword;
  private final String httpsTruststoreType;
  @Nullable
  private final AuthMode authMode;
  
  // Tool category configuration
  private final Set<ToolCategory> enabledToolsets;
  private final boolean isReadOnlyMode;

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    var storagePathString = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    if (storagePathString == null) {
      throw new IllegalArgumentException("STORAGE_PATH environment variable or property must be set");
    }
    this.storagePath = Paths.get(storagePathString);
    this.hostMachineAddress = resolveHostMachineAddress();
    
    // Read configuration values
    this.sonarqubeOrg = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_ORG, null);
    var sonarqubeUrlFromEnv = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_URL, null);
    
    // Check for deprecated SONARQUBE_CLOUD_URL (backward compatibility)
    var sonarqubeCloudUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_URL, null);
    if (sonarqubeCloudUrl != null && sonarqubeUrlFromEnv == null) {
      sonarqubeUrlFromEnv = sonarqubeCloudUrl;
    }
    
    // Determine if this is SonarQube Cloud (presence of ORG indicates SQC)
    this.isSonarCloud = this.sonarqubeOrg != null;
    
    // Apply smart defaults based on mode
    if (this.isSonarCloud) {
      // Cloud mode: default to sonarcloud.io if URL not provided
      this.sonarqubeUrl = sonarqubeUrlFromEnv != null ? sonarqubeUrlFromEnv : "https://sonarcloud.io";
    } else {
      // Server mode: URL is required
      if (sonarqubeUrlFromEnv == null) {
        throw new IllegalArgumentException("SONARQUBE_URL environment variable or property must be set when using SonarQube Server");
      }
      this.sonarqubeUrl = sonarqubeUrlFromEnv;
    }

    this.sonarqubeToken = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOKEN, null);
    if (sonarqubeToken == null) {
      throw new IllegalArgumentException("SONARQUBE_TOKEN environment variable or property must be set");
    }

    this.sonarqubeIdePort = parsePortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IDE_PORT_ENV, null));

    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + " " + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));

    // Parse transport mode: "http", "https", or disabled (stdio)
    var transportMode = requireNonNull(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TRANSPORT, "")).toLowerCase(Locale.getDefault());
    this.isHttpEnabled = transportMode.equals("http") || transportMode.equals("https");
    this.isHttpsEnabled = transportMode.equals("https");
    
    this.httpPort = parseHttpPortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_PORT, "8080"));
    this.httpHost = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_HOST, "127.0.0.1");

    var keystorePathStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_KEYSTORE_PATH, DEFAULT_KEYSTORE_PATH);
    this.httpsKeystorePath = Paths.get(requireNonNull(keystorePathStr));
    this.httpsKeystorePassword = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_KEYSTORE_PASSWORD, DEFAULT_KEYSTORE_PASSWORD);
    this.httpsKeystoreType = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_KEYSTORE_TYPE, DEFAULT_KEYSTORE_TYPE);
    
    var truststorePathStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_TRUSTSTORE_PATH, DEFAULT_TRUSTSTORE_PATH);
    this.httpsTruststorePath = Paths.get(requireNonNull(truststorePathStr));
    this.httpsTruststorePassword = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD, DEFAULT_KEYSTORE_PASSWORD);
    this.httpsTruststoreType = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTPS_TRUSTSTORE_TYPE, DEFAULT_KEYSTORE_TYPE);
    
    this.authMode = parseAuthMode(environment);
    
    // Parse tool category configuration
    var toolsetsStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOOLSETS, null);
    this.enabledToolsets = ToolCategory.parseCategories(toolsetsStr);

    this.isReadOnlyMode = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_READ_ONLY, "false"));
  }

  @NotNull
  public Path getStoragePath() {
    return storagePath;
  }

  @NotNull
  public String getHostMachineAddress() {
    return hostMachineAddress;
  }

  @NotNull
  public Path getLogFilePath() {
    return storagePath.resolve("logs").resolve("mcp.log");
  }

  @Nullable
  public String getSonarqubeOrg() {
    return sonarqubeOrg;
  }

  public String getSonarQubeUrl() {
    return sonarqubeUrl;
  }

  /**
   * Get the SonarQube token.
   * - In stdio mode: Used for all operations.
   * - In HTTP mode: Used only for startup initialization (version check, plugin sync).
   *   Per-request operations use client tokens from Authorization headers.
   */
  public String getSonarQubeToken() {
    return sonarqubeToken;
  }

  public Integer getSonarQubeIdePort() {
    return sonarqubeIdePort;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getAppName() {
    return APP_NAME;
  }

  public boolean isTelemetryEnabled() {
    return isTelemetryEnabled;
  }

  public boolean isSonarCloud() {
    return isSonarCloud;
  }

  public boolean isHttpEnabled() {
    return isHttpEnabled;
  }

  public int getHttpPort() {
    return httpPort;
  }

  public String getHttpHost() {
    return httpHost;
  }

  public boolean isHttpsEnabled() {
    return isHttpsEnabled;
  }

  public Path getHttpsKeystorePath() {
    return httpsKeystorePath;
  }

  public String getHttpsKeystorePassword() {
    return httpsKeystorePassword;
  }

  public String getHttpsKeystoreType() {
    return httpsKeystoreType;
  }

  public Path getHttpsTruststorePath() {
    return httpsTruststorePath;
  }

  public String getHttpsTruststorePassword() {
    return httpsTruststorePassword;
  }

  public String getHttpsTruststoreType() {
    return httpsTruststoreType;
  }

  @Nullable
  public AuthMode getAuthMode() {
    return authMode;
  }

  @CheckForNull
  private static String getValueViaEnvOrPropertyOrDefault(Map<String, String> environment, String propertyName, @Nullable String defaultValue) {
    var value = environment.get(propertyName);
    if (isNullOrBlank(value)) {
      value = System.getProperty(propertyName);
      if (isNullOrBlank(value)) {
        value = defaultValue;
      }
    }
    return value;
  }

  private static boolean isNullOrBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  private static String fetchAppVersion() {
    var implementationVersion = SonarQubeMcpServer.class.getPackage().getImplementationVersion();
    if (implementationVersion == null) {
      implementationVersion = System.getProperty("sonarqube.mcp.server.version");
    }
    if (implementationVersion == null) {
      throw new IllegalArgumentException("SonarQube MCP Server version not found");
    }
    return implementationVersion;
  }

  @CheckForNull
  private static Integer parsePortValue(@Nullable String portStr) {
    if (isNullOrBlank(portStr)) {
      return null;
    }
    try {
      var port = Integer.parseInt(portStr);
      if (port < 64120 || port > 64130) {
        throw new IllegalArgumentException("SONARQUBE_IDE_PORT value must be between 64120 and 64130, got: " + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid SONARQUBE_IDE_PORT value: " + portStr, e);
    }
  }

  private static int parseHttpPortValue(@Nullable String portStr) {
    if (isNullOrBlank(portStr)) {
      return 8080;
    }
    try {
      var port = Integer.parseInt(portStr);
      if (port < 1024 || port > 65535) {
        throw new IllegalArgumentException("SONARQUBE_HTTP_PORT value must be between 1024 and 65535 (unprivileged ports only), got: " + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid SONARQUBE_HTTP_PORT value: " + portStr, e);
    }
  }

  /**
   * Resolves the appropriate host to use for connecting to services on the host machine.
   * Tries first with host.docker.internal (Windows/macOS), and fallback on localhost
   */
  private static String resolveHostMachineAddress() {
    try {
      InetAddress.getByName("host.docker.internal");
      return "host.docker.internal";
    } catch (Exception e) {
      // Continue
    }
    return "localhost";
  }

  @CheckForNull
  private AuthMode parseAuthMode(Map<String, String> environment) {
    if (isHttpEnabled) {
      var authModeStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_AUTH_MODE, "TOKEN");
      return AuthMode.fromString(authModeStr);
    }
    // Stdio mode: No HTTP authentication, AuthenticationFilter not registered
    return null;
  }

  /**
   * Determines if a tool category should be enabled based on configuration.
   * Rules:
   * 1. PROJECTS toolset is always enabled (required to find project keys)
   * 2. If SONARQUBE_TOOLSETS is set, only those toolsets (plus PROJECTS) are enabled
   * 3. If SONARQUBE_TOOLSETS is not set, all toolsets are enabled (default)
   */
  public boolean isToolCategoryEnabled(ToolCategory category) {
    // PROJECTS is always enabled as it's required to find project keys
    if (category == ToolCategory.PROJECTS) {
      return true;
    }
    return enabledToolsets.contains(category);
  }

  public Set<ToolCategory> getEnabledToolsets() {
    return Set.copyOf(enabledToolsets);
  }

  /**
   * Returns whether the server is running in read-only mode.
   * When enabled, only tools marked with readOnlyHint will be available.
   */
  public boolean isReadOnlyMode() {
    return isReadOnlyMode;
  }

}
