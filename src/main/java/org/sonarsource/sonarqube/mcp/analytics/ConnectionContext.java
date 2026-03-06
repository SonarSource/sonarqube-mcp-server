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
package org.sonarsource.sonarqube.mcp.analytics;

import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;

/**
 * Per-session context used to enrich analytics events.
 * Holds connection identifiers resolved from the SonarQube API, and the calling agent info captured during the MCP handshake.
 * In stdio mode this is resolved once at startup. In HTTP mode it is not resolved (per-request user context is unavailable at the server level).
 */
public class ConnectionContext {

  private record CallingAgent(@Nullable String name, @Nullable String version) {
  }

  @Nullable
  private volatile String organizationUuidV4;
  @Nullable
  private volatile String sqsInstallationId;
  @Nullable
  private volatile String userUuid;
  private final AtomicReference<CallingAgent> callingAgent = new AtomicReference<>();

  private ConnectionContext() {
  }

  public static ConnectionContext empty() {
    return new ConnectionContext();
  }

  /**
   * Resolves and populates connection identifiers by querying the SonarQube API in-place,
   * preserving any calling agent info already captured on this instance.
   */
  public void resolveFrom(ServerApi serverApi) {
    if (serverApi.isSonarQubeCloud()) {
      var orgKey = serverApi.getOrganization();
      if (orgKey != null) {
        this.organizationUuidV4 = serverApi.organizationsApi().getOrganizationUuidV4(orgKey);
      }
    } else {
      this.sqsInstallationId = serverApi.systemApi().getStatus().id();
    }
    this.userUuid = serverApi.usersApi().getCurrentUserId();
  }

  /**
   * Captures the calling agent name and version from the MCP handshake.
   * Only set once — subsequent calls are ignored since clientInfo is stable per session.
   */
  public void captureCallingAgent(@Nullable String name, @Nullable String version) {
    callingAgent.compareAndSet(null, new CallingAgent(name, version));
  }

  @CheckForNull
  public String getOrganizationUuidV4() {
    return organizationUuidV4;
  }

  @CheckForNull
  public String getSqsInstallationId() {
    return sqsInstallationId;
  }

  @CheckForNull
  public String getUserUuid() {
    return userUuid;
  }

  @CheckForNull
  public String getCallingAgentName() {
    var agent = callingAgent.get();
    return agent != null ? agent.name() : null;
  }

  @CheckForNull
  public String getCallingAgentVersion() {
    var agent = callingAgent.get();
    return agent != null ? agent.version() : null;
  }

}
