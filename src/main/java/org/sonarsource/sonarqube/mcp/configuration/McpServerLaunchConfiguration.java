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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static java.util.Objects.requireNonNull;

public class McpServerLaunchConfiguration {

  private static final String APP_NAME = "SonarQube MCP Server";

  public static final String SONARCLOUD_IO_URL = "https://sonarcloud.io";
  public static final String SONARQUBE_US_URL = "https://sonarqube.us";

  private static final String STORAGE_PATH = "STORAGE_PATH";
  @Deprecated(forRemoval = true)
  private static final String SONARQUBE_CLOUD_URL = "SONARQUBE_CLOUD_URL";
  private static final String SONARQUBE_URL = "SONARQUBE_URL";
  public static final String SONARQUBE_ORG = "SONARQUBE_ORG";
  public static final String SONARQUBE_TOKEN = "SONARQUBE_TOKEN";
  private static final String SONARQUBE_IDE_PORT_ENV = "SONARQUBE_IDE_PORT";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";
  
  // Tool category configuration
  public static final String SONARQUBE_TOOLSETS = "SONARQUBE_TOOLSETS";
  public static final String SONARQUBE_READ_ONLY = "SONARQUBE_READ_ONLY";
  
  // Default project key configuration
  public static final String SONARQUBE_PROJECT_KEY = "SONARQUBE_PROJECT_KEY";

  // Logging configuration
  private static final String SONARQUBE_LOG_TO_FILE_DISABLED = "SONARQUBE_LOG_TO_FILE_DISABLED";

  // Force SonarQube Cloud detection for non-standard deployments
  private static final String SONARQUBE_IS_CLOUD = "SONARQUBE_IS_CLOUD";
  // Override the API subdomain base URL for non-standard SonarQube Cloud deployments
  private static final String SONARQUBE_CLOUD_API_URL = "SONARQUBE_CLOUD_API_URL";
  
  // HTTP/HTTPS transport configuration
  private static final String SONARQUBE_TRANSPORT = "SONARQUBE_TRANSPORT";
  private static final String SONARQUBE_HTTP_PORT = "SONARQUBE_HTTP_PORT";
  private static final String SONARQUBE_HTTP_HOST = "SONARQUBE_HTTP_HOST";
  private static final String SONARQUBE_HTTP_ALLOWED_ORIGINS = "SONARQUBE_HTTP_ALLOWED_ORIGINS";
  
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
  private final String sonarqubeCloudApiUrl;
  @Nullable
  private final String sonarqubeOrg;
  @Nullable
  private final String sonarqubeToken;
  private final Integer sonarqubeIdePort;
  private final String appVersion;
  private final String userAgent;
  private final boolean isTelemetryEnabled;
  private final boolean isSonarQubeCloud;
  
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
  private final List<String> httpAllowedOrigins;

  // Tool category configuration
  private final Set<ToolCategory> enabledToolsets;
  private final boolean isReadOnlyMode;
  
  // Default project key configuration
  @Nullable
  private final String sonarqubeProjectKey;

  private final boolean isFileLoggingDisabled;

  private final String mcpServerId;

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    var storagePathString = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    if (storagePathString == null) {
      throw new IllegalArgumentException("STORAGE_PATH environment variable or property must be set");
    }
    this.storagePath = Paths.get(storagePathString);
    this.hostMachineAddress = resolveHostMachineAddress();

    var transportMode = requireNonNull(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TRANSPORT, "")).toLowerCase(Locale.getDefault());
    this.isHttpEnabled = transportMode.equals("http") || transportMode.equals("https");
    this.isHttpsEnabled = transportMode.equals("https");

    // Read configuration values
    this.sonarqubeOrg = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_ORG, null);
    var sonarqubeUrlFromEnv = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_URL, null);
    
    // Check for deprecated SONARQUBE_CLOUD_URL (backward compatibility)
    var sonarqubeCloudUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_URL, null);
    if (sonarqubeCloudUrl != null && sonarqubeUrlFromEnv == null) {
      sonarqubeUrlFromEnv = sonarqubeCloudUrl;
    }

    var forceSonarQubeCloud = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IS_CLOUD, "false"));
    validateStdioConfiguration(isHttpEnabled, sonarqubeUrlFromEnv, this.sonarqubeOrg);
    this.isSonarQubeCloud = resolveSonarQubeCloud(forceSonarQubeCloud, this.sonarqubeOrg, sonarqubeUrlFromEnv);
    this.sonarqubeUrl = resolveUrl(this.isSonarQubeCloud, sonarqubeUrlFromEnv);

    this.sonarqubeCloudApiUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_API_URL, null);

    this.sonarqubeToken = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOKEN, null);
    // In HTTP mode the token is provided per-request via the Authorization: Bearer header; not required at startup.
    if (sonarqubeToken == null && !isHttpEnabled) {
      throw new IllegalArgumentException("SONARQUBE_TOKEN environment variable or property must be set");
    }

    this.sonarqubeIdePort = parsePortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IDE_PORT_ENV, null));

    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + " " + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));
    
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
    this.httpAllowedOrigins = parseAllowedOrigins(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_ALLOWED_ORIGINS, null));

    // Parse tool category configuration
    var toolsetsStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOOLSETS, null);
    this.enabledToolsets = ToolCategory.parseCategories(toolsetsStr);

    this.isReadOnlyMode = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_READ_ONLY, "false"));

    this.sonarqubeProjectKey = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_PROJECT_KEY, null);

    this.isFileLoggingDisabled = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_LOG_TO_FILE_DISABLED, "false"));

    this.mcpServerId = UUID.randomUUID().toString();
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
   * Returns the explicit API subdomain base URL configured via SONARQUBE_CLOUD_API_URL, or null if not set.
   */
  @Nullable
  public String getSonarQubeCloudApiUrl() {
    return sonarqubeCloudApiUrl;
  }

  /**
   * Get the SonarQube token.
   * - In stdio mode: Always non-null; used for all operations.
   * - In HTTP mode: Optional startup token (null when not configured).
   *   Per-request operations use client tokens from the Authorization: Bearer header.
   */
  @Nullable
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

  public boolean isSonarQubeCloud() {
    return isSonarQubeCloud;
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

  public List<String> getHttpAllowedOrigins() {
    return httpAllowedOrigins;
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
   * In stdio mode, either SONARQUBE_URL or SONARQUBE_ORG must be set.
   * There is no per-request org resolution, so connecting to SonarQube without a URL or org key makes no sense.
   * In HTTP mode, no validation is needed: the server defaults to sonarcloud.io and resolves from the Authorization header at request time.
   */
  private static void validateStdioConfiguration(boolean isHttpEnabled, @Nullable String url, @Nullable String org) {
    if (!isHttpEnabled && url == null && org == null) {
      throw new IllegalArgumentException(
        "SONARQUBE_URL or SONARQUBE_ORG must be set. " +
          "Set SONARQUBE_URL to your SonarQube Server URL or SonarQube Cloud URL, " +
          "or set SONARQUBE_ORG to connect to SonarQube Cloud."
      );
    }
  }

  /**
   * Resolves whether the server is connecting to SonarQube Cloud.
   * Signal priority (highest to lowest):
   *   1. SONARQUBE_IS_CLOUD=true → explicit override
   *   2. SONARQUBE_ORG set → org implies SQC
   *   3. SONARQUBE_URL is a known SQC hostname (sonarcloud.io, sonarqube.us, …)
   *   4. No SONARQUBE_URL → default to SQC (HTTP mode only after stdio validation)
   *   5. SONARQUBE_URL is unknown host → SQS
   */
  private static boolean resolveSonarQubeCloud(boolean forceSonarCloud, @Nullable String org, @Nullable String url) {
    return forceSonarCloud || org != null || url == null || isSonarQubeCloudUrl(url);
  }

  /**
   * Resolves the effective server URL.
   * For SQC without an explicit URL, defaults to sonarcloud.io.
   * For SQS, the URL is guaranteed non-null at this point (stdio validation ensures this).
   */
  private static String resolveUrl(boolean isSonarQubeCloud, @Nullable String url) {
    if (isSonarQubeCloud) {
      return url != null ? url : SONARCLOUD_IO_URL;
    }
    return url;
  }

  /**
   * Returns true if the given URL points to a SonarQube Cloud instance
   */
  public static boolean isSonarQubeCloudUrl(@Nullable String url) {
    if (url == null) {
      return false;
    }
    try {
      var host = URI.create(url).getHost();
      return "sonarcloud.io".equals(host) || (host != null && host.endsWith(".sonarcloud.io"))
        || "sonarqube.us".equals(host) || (host != null && host.endsWith(".sonarqube.us"));
    } catch (IllegalArgumentException e) {
      return false;
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

  private static List<String> parseAllowedOrigins(@Nullable String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return List.of();
    }
    return Arrays.stream(rawValue.split(","))
      .map(String::trim)
      .filter(s -> !s.isBlank())
      .toList();
  }

  /**
   * Determines if a tool category should be enabled based on configuration.
   * Rules:
   * 1. PROJECTS toolset is always enabled (required to find project keys)
   * 2. If SONARQUBE_TOOLSETS is set, only those toolsets (plus PROJECTS) are enabled
   * 3. If SONARQUBE_TOOLSETS is not set, only default important toolsets are enabled
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

  public boolean isFileLoggingDisabled() {
    return isFileLoggingDisabled;
  }

  /**
   * Returns the default project key configured via SONARQUBE_PROJECT_KEY.
   * When set, all tools that accept a projectKey will use this as a fallback when no projectKey
   * is provided in the tool call arguments.
   */
  @Nullable
  public String getProjectKey() {
    return sonarqubeProjectKey;
  }

  /**
   * Returns the unique identifier for this MCP server instance, generated at startup.
   */
  public String getMcpServerId() {
    return mcpServerId;
  }

}
