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
package org.sonarsource.sonarqube.mcp;

import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

public class SonarQubeVersionChecker {

  private static final McpLogger LOG = McpLogger.getInstance();
  // this version does not exist, but it enables us to check for both SQS and SQCB
  private static final Version MINIMAL_SUPPORTED_SONARQUBE_SERVER_VERSION = Version.create("10.9");

  private final ServerApi serverApi;

  public SonarQubeVersionChecker(ServerApi serverApi) {
    this.serverApi = serverApi;
  }

  public void failIfSonarQubeServerVersionIsNotSupported() {
    if (!serverApi.isSonarQubeCloud()) {
      var version = Version.create(serverApi.systemApi().getStatus().version());
      if (!version.satisfiesMinRequirement(MINIMAL_SUPPORTED_SONARQUBE_SERVER_VERSION)) {
        throw new IllegalStateException("SonarQube server version is not supported, minimal version is SQS 2025.1 or SQCB 25.1");
      }
    }
  }

  public boolean isSonarQubeServerVersionHigherOrEqualsThan(String minVersion) {
    if (!serverApi.isSonarQubeCloud()) {
      var version = Version.create(serverApi.systemApi().getStatus().version());
      return version.satisfiesMinRequirement(Version.create(minVersion));
    }
    return false;
  }

  public boolean isScaEnabled() {
    try {
      var settingsResponse = serverApi.settingsApi().getSettings();
      return settingsResponse.isBooleanSettingEnabled("sonar.sca.enabled");
    } catch (Exception e) {
      LOG.error("Failed to check sonar.sca.enabled setting. Assuming SCA is disabled.", e);
      return false;
    }
  }

}
