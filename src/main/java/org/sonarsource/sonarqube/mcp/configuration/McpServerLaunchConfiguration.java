/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;

import static java.util.Objects.requireNonNull;

public class McpServerLaunchConfiguration {

  private static final String APP_NAME = "SonarQube MCP Server";

  private static final String STORAGE_PATH = "STORAGE_PATH";
  private static final String SONARQUBE_CLOUD_URL = "SONARQUBE_CLOUD_URL";
  private static final String SONARQUBE_URL = "SONARQUBE_URL";
  private static final String SONARQUBE_ORG = "SONARQUBE_ORG";
  private static final String SONARQUBE_TOKEN = "SONARQUBE_TOKEN";
  private static final String SONARQUBE_IDE_PORT_ENV = "SONARQUBE_IDE_PORT";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";
  
  // HTTP transport configuration
  private static final String SONARQUBE_HTTP_ENABLED = "SONARQUBE_HTTP_ENABLED";
  private static final String SONARQUBE_HTTP_PORT = "SONARQUBE_HTTP_PORT";
  private static final String SONARQUBE_HTTP_HOST = "SONARQUBE_HTTP_HOST";

  private final Path storagePath;
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

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    var storagePathString = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    if (storagePathString == null) {
      throw new IllegalArgumentException("STORAGE_PATH environment variable or property must be set");
    }
    this.storagePath = Paths.get(storagePathString);
    var sonarqubeCloudUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_URL, "https://sonarcloud.io");
    this.sonarqubeUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_URL, sonarqubeCloudUrl);
    this.sonarqubeOrg = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_ORG, null);
    this.sonarqubeToken = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOKEN, null);
    if (sonarqubeToken == null) {
      throw new IllegalArgumentException("SONARQUBE_TOKEN environment variable or property must be set");
    }
    this.sonarqubeIdePort = parsePortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_IDE_PORT_ENV, null));

    this.isSonarCloud = requireNonNull(sonarqubeCloudUrl).equals(this.sonarqubeUrl);

    if (this.isSonarCloud && this.sonarqubeOrg == null) {
      throw new IllegalArgumentException("SONARQUBE_ORG environment variable must be set when using SonarQube Cloud");
    }

    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + " " + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));

    this.isHttpEnabled = Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_ENABLED, "false"));
    this.httpPort = parseHttpPortValue(getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_PORT, "8080"));
    this.httpHost = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_HTTP_HOST, "127.0.0.1");
  }

  @NotNull
  public Path getStoragePath() {
    return storagePath;
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
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("SONARQUBE_HTTP_PORT value must be between 1 and 65535, got: " + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid SONARQUBE_HTTP_PORT value: " + portStr, e);
    }
  }

}
