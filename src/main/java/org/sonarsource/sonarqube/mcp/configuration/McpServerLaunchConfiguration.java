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
package org.sonarsource.sonarqube.mcp.configuration;

import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.authentication.OAuthConfiguration;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripEnd;

public final class McpServerLaunchConfiguration {

  private static final String SONARQUBE_OAUTH_RESOURCE = "SONARQUBE_OAUTH_RESOURCE";
  private static final String SONARQUBE_OAUTH_ISSUER = "SONARQUBE_OAUTH_ISSUER";
  private static final List<String> OAUTH_SETTINGS = List.of(
    SONARQUBE_OAUTH_ISSUER, SONARQUBE_OAUTH_RESOURCE, "SONARQUBE_OAUTH_CLOUD_AUDIENCE", "SONARQUBE_OAUTH_CLIENT_ID",
    "SONARQUBE_OAUTH_CLIENT_SECRET", "SONARQUBE_OAUTH_METADATA_URL", "SONARQUBE_OAUTH_CACHE_TTL_SECONDS", "SONARQUBE_OAUTH_CACHE_SIZE");

  private static final String APP_NAME = "SonarQube MCP Server";
  private static final String HTTPS = "https";

  public static final String SONARCLOUD_IO_URL = "https://sonarcloud.io";
  public static final String SONARQUBE_US_URL = "https://sonarqube.us";

  private static final String STORAGE_PATH = "STORAGE_PATH";
  @Deprecated(forRemoval = true)
  private static final String SONARQUBE_CLOUD_URL = "SONARQUBE_CLOUD_URL";
  private static final String SONARQUBE_URL = "SONARQUBE_URL";
  public static final String SONARQUBE_ORG = "SONARQUBE_ORG";
  public static final String SONARQUBE_TOKEN = "SONARQUBE_TOKEN";
  private static final String SONARQUBE_IDE_PORT_ENV = "SONARQUBE_IDE_PORT";
  private static final String SONARQUBE_FORCE_LOCAL_ANALYSIS = "SONARQUBE_FORCE_LOCAL_ANALYSIS";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";
  
  // Tool category configuration
  public static final String SONARQUBE_TOOLSETS = "SONARQUBE_TOOLSETS";
  public static final String SONARQUBE_READ_ONLY = "SONARQUBE_READ_ONLY";
  
  // Default project key configuration
  public static final String SONARQUBE_PROJECT_KEY = "SONARQUBE_PROJECT_KEY";

  // Active when a volume is mounted here (e.g. -v /your/project:/app/mcp-workspace).
  public static final String MCP_WORKSPACE_PATH = "/app/mcp-workspace";
  // System property to override the workspace path in tests
  public static final String MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY = "sonarqube.mcp.workspace.path";

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
  
  private static final String SONARQUBE_MCP_IN_CONTAINER = "SONARQUBE_MCP_IN_CONTAINER";

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
  private final boolean forceLocalAnalysis;
  
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
  private final List<String> httpAllowedOrigins;
  @Nullable
  private final OAuthConfiguration oauthConfiguration;
  @Nullable
  private final String oauthMetadataUrl;

  // Tool category configuration
  private final Set<ToolCategory> enabledToolsets;
  private final boolean isReadOnlyMode;
  
  // Default project key configuration
  @Nullable
  private final String sonarqubeProjectKey;

  @Nullable
  private final Path workspacePath;

  private final boolean isFileLoggingDisabled;
  private final boolean isRunningInContainer;

  private final String mcpServerId;

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    var storagePathString = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    if (storagePathString == null) {
      throw new IllegalArgumentException("STORAGE_PATH environment variable or property must be set");
    }
    this.storagePath = Paths.get(storagePathString);
    this.hostMachineAddress = resolveHostMachineAddress();

    this.isHttpEnabled = isHttpTransport(environment);
    this.isHttpsEnabled = HTTPS.equals(transportMode(environment));

    // Read configuration values.
    // SONARQUBE_TOKEN, SONARQUBE_ORG, and SONARQUBE_URL may be forwarded as literal "${VAR}" strings by MCP clients
    // when the corresponding host environment variable is unset; treat those as unset.
    this.sonarqubeOrg = ignoreUnresolvedPlaceholder(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_ORG, null), SONARQUBE_ORG);
    var sonarqubeUrlFromEnv = ignoreUnresolvedPlaceholder(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_URL, null), SONARQUBE_URL);
    
    // Check for deprecated SONARQUBE_CLOUD_URL (backward compatibility)
    var sonarqubeCloudUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_URL, null);
    if (sonarqubeCloudUrl != null && sonarqubeUrlFromEnv == null) {
      sonarqubeUrlFromEnv = sonarqubeCloudUrl;
    }

    var forceSonarQubeCloud = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IS_CLOUD, "false"));
    this.isSonarQubeCloud = resolveSonarQubeCloud(forceSonarQubeCloud, this.sonarqubeOrg, sonarqubeUrlFromEnv);
    this.sonarqubeUrl = resolveUrl(this.isSonarQubeCloud, sonarqubeUrlFromEnv);

    this.sonarqubeCloudApiUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_API_URL, null);

    this.sonarqubeToken = ignoreUnresolvedPlaceholder(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOKEN, null), SONARQUBE_TOKEN);
    // In HTTP mode the token is provided per-request via the Authorization: Bearer header; not required at startup.
    if (sonarqubeToken == null && !isHttpEnabled) {
      throw new IllegalArgumentException("SONARQUBE_TOKEN environment variable or property must be set");
    }

    this.sonarqubeIdePort = parsePortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IDE_PORT_ENV, null));

    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + " " + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));
    this.forceLocalAnalysis = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_FORCE_LOCAL_ANALYSIS, "false"));
    
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
    
    var oauthEnabled = isHttpEnabled && hasOAuthSettings(environment);
    if (oauthEnabled) {
      validateSuppliedOAuthSettings(environment);
    }
    if (oauthEnabled && sonarqubeOrg != null) {
      throw new IllegalArgumentException("SONARQUBE_ORG cannot be configured with OAuth. Use the tool organization argument.");
    }
    if (oauthEnabled && !this.isSonarQubeCloud) {
      throw new IllegalArgumentException("OAuth is supported only for SonarQube Cloud");
    }
    var metadataUrl = oauthEnabled ? getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_METADATA_URL", null) : null;
    this.oauthMetadataUrl = metadataUrl == null ? null : OAuthConfiguration.validatedHttpsUri(metadataUrl).toString();
    this.oauthConfiguration = oauthEnabled ? new OAuthConfiguration(
      getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_OAUTH_ISSUER, null),
      getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_OAUTH_RESOURCE, null),
      getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_CLOUD_AUDIENCE", null),
      getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_CLIENT_ID", null),
      getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_CLIENT_SECRET", null),
      Duration.ofSeconds(Long.parseLong(getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_CACHE_TTL_SECONDS", "60"))),
      Integer.parseInt(getValueViaEnvOrPropertyOrDefault(environment, "SONARQUBE_OAUTH_CACHE_SIZE", "256"))) : null;
    validateOAuthCloudConfiguration(sonarqubeUrlFromEnv);
    this.httpAllowedOrigins = parseAllowedOrigins(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_ALLOWED_ORIGINS, null));

    // Parse tool category configuration
    var toolsetsStr = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOOLSETS, null);
    this.enabledToolsets = Set.copyOf(ToolCategory.parseCategories(toolsetsStr, getTransportMode()));

    this.isReadOnlyMode = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_READ_ONLY, "false"));

    this.sonarqubeProjectKey = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_PROJECT_KEY, null);

    this.workspacePath = resolveWorkspacePath();

    this.isFileLoggingDisabled = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_LOG_TO_FILE_DISABLED, "false"));
    this.isRunningInContainer = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_MCP_IN_CONTAINER, "false"));

    this.mcpServerId = UUID.randomUUID().toString();
  }

  private static boolean hasOAuthSettings(Map<String, String> environment) {
    return OAUTH_SETTINGS.stream().anyMatch(setting -> environment.containsKey(setting) || System.getProperties().containsKey(setting));
  }

  private static void validateSuppliedOAuthSettings(Map<String, String> environment) {
    for (var setting : OAUTH_SETTINGS) {
      if ((environment.containsKey(setting) || System.getProperties().containsKey(setting))
        && getValueViaEnvOrPropertyOrDefault(environment, setting, null) == null) {
        throw new IllegalArgumentException(setting + " must not be blank when OAuth is configured");
      }
    }
  }

  private void validateOAuthCloudConfiguration(@Nullable String sonarqubeUrlFromEnv) {
    if (oauthConfiguration != null && (sonarqubeUrlFromEnv == null || sonarqubeCloudApiUrl == null
      || !oauthConfiguration.cloudAudience().equals(stripEnd(sonarqubeCloudApiUrl, "/") + "/"))) {
      throw new IllegalArgumentException("OAuth requires explicit SonarQube Cloud URLs matching the configured Cloud audience");
    }
    if (oauthConfiguration != null) {
      OAuthConfiguration.validatedHttpsUri(sonarqubeUrl);
    }
  }

  @Nullable
  public OAuthConfiguration getOAuthConfiguration() {
    return oauthConfiguration;
  }

  public Path getStoragePath() {
    return storagePath;
  }

  public String getHostMachineAddress() {
    return hostMachineAddress;
  }

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

  public static String getAppName() {
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

  public TransportMode getTransportMode() {
    return isHttpEnabled ? TransportMode.HTTP : TransportMode.STDIO;
  }

  /**
   * True when {@code SONARQUBE_TRANSPORT} is {@code http} or {@code https}.
   * Safe to call even when constructing {@link McpServerLaunchConfiguration} would fail.
   */
  public static boolean isHttpTransport(Map<String, String> environment) {
    var mode = transportMode(environment);
    return "http".equals(mode) || HTTPS.equals(mode);
  }

  private static String transportMode(Map<String, String> environment) {
    return requireNonNull(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TRANSPORT, "")).toLowerCase(Locale.getDefault());
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
  public String getOAuthMetadataUrl() {
    return oauthMetadataUrl;
  }

  public List<String> getHttpAllowedOrigins() {
    return httpAllowedOrigins;
  }

  @Nullable
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

  /**
   * Returns null when {@code value} is the literal unresolved placeholder {@code ${propertyName}},
   * otherwise returns {@code value} unchanged. MCP clients forward such literals when the
   * corresponding host environment variable is unset.
   */
  @Nullable
  private static String ignoreUnresolvedPlaceholder(@Nullable String value, String envName) {
    return ("${" + envName + "}").equals(value) ? null : value;
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

  @Nullable
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
      if (port != 0 && (port < 1024 || port > 65535)) {
        throw new IllegalArgumentException("SONARQUBE_HTTP_PORT value must be 0 (automatic) or between 1024 and 65535 (unprivileged ports only), got: " + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid SONARQUBE_HTTP_PORT value: " + portStr, e);
    }
  }

  /**
   * Resolves whether the server is connecting to SonarQube Cloud.
   * Signal priority (highest to lowest):
   *   1. SONARQUBE_IS_CLOUD=true → explicit override
   *   2. SONARQUBE_ORG set → org implies SQC
   *   3. SONARQUBE_URL is a known SQC hostname (sonarcloud.io, sonarqube.us, …)
   *   4. No SONARQUBE_URL → Cloud (defaults to sonarcloud.io)
   *   5. SONARQUBE_URL is unknown host → SQS
   */
  private static boolean resolveSonarQubeCloud(boolean forceSonarCloud, @Nullable String org, @Nullable String url) {
    if (forceSonarCloud || org != null || isSonarQubeCloudUrl(url)) {
      return true;
    }
    return url == null;
  }

  /**
   * Resolves the effective server URL.
   * For SQC without an explicit URL, defaults to sonarcloud.io.
   * For SQS, the URL must be set explicitly in stdio mode.
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

  @Nullable
  private static Path resolveWorkspacePath() {
    var override = System.getProperty(MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    var pathStr = (override != null && !override.isBlank()) ? override : MCP_WORKSPACE_PATH;
    var candidate = Paths.get(pathStr).toAbsolutePath().normalize();
    return candidate.toFile().isDirectory() ? candidate : null;
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

  public boolean isRunningInContainer() {
    return isRunningInContainer;
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

  @Nullable
  public Path getWorkspacePath() {
    return workspacePath;
  }

  public boolean isForceLocalAnalysis() {
    return forceLocalAnalysis;
  }

  /**
   * Returns the unique identifier for this MCP server instance, generated at startup.
   */
  public String getMcpServerId() {
    return mcpServerId;
  }

}
