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

import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;

public class McpServerLaunchConfiguration {

  private static final String APP_NAME = "SonarQube MCP Server";
  private static final String SONARCLOUD_URL = "https://sonarcloud.io";
  private static final String STORAGE_PATH = "STORAGE_PATH";
  private static final String PLUGINS_PATH = "PLUGINS_PATH";
  private static final String SONARQUBE_URL = "SONARQUBE_URL";
  private static final String SONARQUBE_ORG = "SONARQUBE_ORG";
  private static final String SONARQUBE_TOKEN = "SONARQUBE_TOKEN";
  private static final String TELEMETRY_DISABLED = "TELEMETRY_DISABLED";

  private final String storagePath;
  @Nullable
  private final String pluginsPath;
  private final String sonarqubeUrl;
  @Nullable
  private final String sonarqubeOrg;
  @Nullable
  private final String sonarqubeToken;
  private final String appVersion;
  private final String userAgent;
  private final boolean isTelemetryEnabled;
  private final boolean isSonarCloud;

  public McpServerLaunchConfiguration(Map<String, String> environment) {
    this.storagePath = getValueViaEnvOrPropertyOrDefault(environment, STORAGE_PATH, null);
    if (storagePath == null) {
      throw new IllegalArgumentException("STORAGE_PATH environment variable or property must be set");
    }
    this.sonarqubeUrl = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_URL, SONARCLOUD_URL);
    this.sonarqubeOrg = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_ORG, null);
    this.sonarqubeToken = getValueViaEnvOrPropertyOrDefault(environment, SONARQUBE_TOKEN, null);

    this.isSonarCloud = SONARCLOUD_URL.equals(this.sonarqubeUrl);

    if (this.sonarqubeToken != null && (this.isSonarCloud && this.sonarqubeOrg == null)) {
      throw new IllegalArgumentException("SONARQUBE_ORG environment variable must be set when using SonarQube Cloud");
    }

    this.pluginsPath = getValueViaEnvOrPropertyOrDefault(environment, PLUGINS_PATH, null);
    this.appVersion = fetchAppVersion();
    this.userAgent = APP_NAME + " " + appVersion;
    this.isTelemetryEnabled = !Boolean.parseBoolean(getValueViaEnvOrPropertyOrDefault(environment, TELEMETRY_DISABLED, "false"));
  }

  @NotNull
  public String getStoragePath() {
    return storagePath;
  }

  @Nullable
  public String getPluginsPath() {
    return pluginsPath;
  }

  @Nullable
  public String getSonarqubeOrg() {
    return sonarqubeOrg;
  }

  public String getSonarQubeUrl() {
    return sonarqubeUrl;
  }

  @Nullable
  public String getSonarQubeToken() {
    return sonarqubeToken;
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

  @CheckForNull
  private static String getValueViaEnvOrPropertyOrDefault(Map<String, String> environment, String propertyName, @Nullable String defaultValue) {
    var property = environment.get(propertyName);
    if (property == null) {
      property = System.getProperty(propertyName);
    }
    if (property == null) {
      property = defaultValue;
    }
    if (property != null && property.isBlank()) {
      property = null;
    }
    return property;
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

}
