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
package org.sonarsource.sonarqube.mcp;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

public class SonarQubeVersionChecker {

  static final String UNSUPPORTED_SERVER_VERSION_MESSAGE =
    "SonarQube server version is not supported, minimum version is SQS 2025.1 or SQCB 25.1";

  private final ServerApi serverApi;

  public SonarQubeVersionChecker(ServerApi serverApi) {
    this.serverApi = serverApi;
  }

  public void failIfSonarQubeServerVersionIsNotSupported() {
    if (!serverApi.isSonarQubeCloud()) {
      var version = Version.create(serverApi.systemApi().getStatus().version());
      if (!version.isSupportedSonarQubeServerVersion()) {
        throw new IllegalStateException(UNSUPPORTED_SERVER_VERSION_MESSAGE);
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

}
