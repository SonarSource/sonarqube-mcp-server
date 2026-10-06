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
package org.sonarsource.sonarqube.mcp.transport;

import jakarta.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;

public record HttpTransportSettings(int port, String host, AuthMode authMode, boolean isSonarQubeCloud, @Nullable String serverOrg,
  TlsSettings tls, RequestSettings requests) {

  public static HttpTransportSettings from(McpServerLaunchConfiguration configuration) {
    return new HttpTransportSettings(configuration.getHttpPort(), configuration.getHttpHost(), configuration.getAuthMode(),
      configuration.isSonarQubeCloud(), configuration.getSonarqubeOrg(),
      new TlsSettings(configuration.isHttpsEnabled(), configuration.getHttpsKeystorePath(), configuration.getHttpsKeystorePassword(),
        configuration.getHttpsKeystoreType(), configuration.getHttpsTruststorePath(), configuration.getHttpsTruststorePassword(), configuration.getHttpsTruststoreType()),
      new RequestSettings(configuration.getHttpAllowedOrigins(), configuration.getAppVersion(), configuration.isRunningInContainer()));
  }

  public record TlsSettings(boolean enabled, @Nullable Path keystorePath, @Nullable String keystorePassword, @Nullable String keystoreType,
    @Nullable Path truststorePath, @Nullable String truststorePassword, @Nullable String truststoreType) {
    @Override
    public String toString() {
      return "TlsSettings[enabled=" + enabled + ", keystorePath=" + keystorePath + ", truststorePath=" + truststorePath + ", credentials=redacted]";
    }
  }

  public record RequestSettings(List<String> allowedOrigins, String appVersion, boolean isRunningInContainer) { }
}


