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

import java.util.concurrent.CompletionException;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

public class SonarQubeVersionChecker {

  static final String UNSUPPORTED_SERVER_VERSION_MESSAGE =
    "SonarQube server version is not supported, minimum version is SQS 2025.1 or SQCB 25.1";
  private static final int MAX_STATUS_ATTEMPTS = 3;
  private static final long RETRY_DELAY_MILLIS = 500;

  private final ServerApi serverApi;

  public SonarQubeVersionChecker(ServerApi serverApi) {
    this.serverApi = serverApi;
  }

  public void failIfSonarQubeServerVersionIsNotSupported() {
    if (!serverApi.isSonarQubeCloud()) {
      for (int attempt = 1; attempt <= MAX_STATUS_ATTEMPTS; attempt++) {
        try {
          var version = Version.create(serverApi.systemApi().getStatus().version());
          if (!version.isSupportedSonarQubeServerVersion()) {
            throw new IllegalStateException(UNSUPPORTED_SERVER_VERSION_MESSAGE);
          }
          return;
        } catch (CompletionException | ServerInternalErrorException e) {
          if (attempt == MAX_STATUS_ATTEMPTS) {
            throw new IllegalStateException("SonarQube Server is unavailable after " + MAX_STATUS_ATTEMPTS
              + " attempts. Check SONARQUBE_URL and wait for SonarQube to become healthy, then restart the MCP server.", e);
          }
          waitBeforeRetry();
        }
      }
    }
  }

  private static void waitBeforeRetry() {
    try {
      Thread.sleep(RETRY_DELAY_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for SonarQube Server to become available", e);
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
