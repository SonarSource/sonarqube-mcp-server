/*
 * Sonar MCP Server
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
package org.sonar.mcp.configuration;

import java.util.Map;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonar.mcp.SonarMcpServer;

public class McpServerLaunchConfiguration {

  private static final String APP_NAME = "Sonar MCP Server";

  private static final String SONARCLOUD_URL = "https://sonarcloud.io";

  private static final String STORAGE_PATH = "STORAGE_PATH";
  private static final String SONARQUBE_CLOUD_URL = "SONARQUBE_CLOUD_URL";
  private static final String SONARQUBE_CLOUD_ORG = "SONARQUBE_CLOUD_ORG";
  private static final String SONARQUBE_CLOUD_TOKEN = "SONARQUBE_CLOUD_TOKEN";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";

  private final String storagePath;
  private final String sonarqubeCloudUrl;
  @Nullable
  private final String sonarqubeCloudOrg;
  @Nullable
  private final String sonarqubeCloudToken;
  private final String appVersion;
  private final String userAgent;
  private final boolean isTelemetryEnabled;

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    this.storagePath = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    Objects.requireNonNull(storagePath, "STORAGE_PATH environment variable or property must be set");
    this.sonarqubeCloudUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_URL, SONARCLOUD_URL);
    this.sonarqubeCloudOrg = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_ORG, null);
    this.sonarqubeCloudToken = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_CLOUD_TOKEN, null);
    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));
  }

  @NotNull
  public String getStoragePath() {
    return storagePath;
  }

  @NotNull
  public String getSonarqubeCloudUrl() {
    return sonarqubeCloudUrl;
  }

  @Nullable
  public String getSonarqubeCloudOrg() {
    return sonarqubeCloudOrg;
  }

  @Nullable
  public String getSonarqubeCloudToken() {
    return sonarqubeCloudToken;
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

  @CheckForNull
  private static String getValueViaEnvOrPropertyOrDefault(Map<String, String> environment, String propertyName, @Nullable String defaultValue) {
    var property = environment.get(propertyName);
    if (property == null) {
      property = System.getProperty(propertyName);
    }
    if (property == null) {
      property = defaultValue;
    }
    return property;
  }

  private static String fetchAppVersion() {
    var implementationVersion = SonarMcpServer.class.getPackage().getImplementationVersion();
    if (implementationVersion == null) {
      implementationVersion = System.getProperty("sonar.mcp.server.version");
    }
    Objects.requireNonNull(implementationVersion, "Sonar MPC Server version not found");
    return implementationVersion;
  }

}
