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
package org.sonarsource.sonarqube.mcp.analytics;

import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;

/**
 * Per-session (or per-request) context used to enrich analytics events.
 * Holds connection identifiers resolved from the SonarQube API, and the calling agent info captured during the MCP handshake.
 * <ul>
 *   <li>In stdio mode: a single shared instance is populated once at startup via {@link #resolveFrom(ServerApi)}
 *       and reused for all tool calls. Organization UUIDs are cached by the session's {@link ServerApi}.
 *       {@link #captureCallingAgent} is also written to this instance.</li>
 *   <li>In HTTP mode: a fresh instance is created per tool call inside the async analytics dispatch task.
 *       {@link #resolveFrom(ServerApi)} is called there with the request-scoped {@link ServerApi},
 *       so SonarQube API calls (user UUID, org UUID, installation ID) never block the tool response.</li>
 * </ul>
 */
public class ConnectionContext {

  public record Snapshot(@Nullable String organizationUuidV4, @Nullable String sqsInstallationId, @Nullable String userUuid,
    @Nullable String callingAgentName, @Nullable String callingAgentVersion) {
  }

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
      this.organizationUuidV4 = resolveOrganizationUuid(serverApi);
    }
    resolveIdentityFrom(serverApi);
  }

  private synchronized void resolveIdentityFrom(ServerApi serverApi) {
    if (!serverApi.isSonarQubeCloud() && sqsInstallationId == null) {
      this.sqsInstallationId = serverApi.systemApi().getStatus().id();
    }
    if (userUuid == null) {
      this.userUuid = serverApi.usersApi().getCurrentUserId();
    }
  }

  public Snapshot snapshot() {
    var agent = callingAgent.get();
    return new Snapshot(organizationUuidV4, sqsInstallationId, userUuid,
      agent != null ? agent.name() : null, agent != null ? agent.version() : null);
  }

  /**
   * Reuses resolved stdio identity and captured agent information, retrying unresolved identity asynchronously.
   * Creating an invocation snapshot never changes the default organization identifiers.
   */
  public Snapshot snapshotFor(ServerApi serverApi, Snapshot captured) {
    var selectedOrgUuid = serverApi.isSonarQubeCloud() ? resolveOrganizationUuid(serverApi) : null;
    resolveIdentityFrom(serverApi);
    return new Snapshot(selectedOrgUuid, sqsInstallationId, userUuid, captured.callingAgentName(), captured.callingAgentVersion());
  }

  @Nullable
  private static String resolveOrganizationUuid(ServerApi serverApi) {
    var orgKey = serverApi.getOrganization();
    if (orgKey == null || orgKey.isBlank()) {
      return null;
    }
    return serverApi.resolveOrganizationUuid(ResolvedOrganization.fromKey(orgKey));
  }

  /**
   * Captures the calling agent name and version from the MCP handshake.
   * Only set once — subsequent calls are ignored since clientInfo is stable per session.
   */
  public void captureCallingAgent(@Nullable String name, @Nullable String version) {
    callingAgent.compareAndSet(null, new CallingAgent(name, version));
  }

  @Nullable
  public String getOrganizationUuidV4() {
    return organizationUuidV4;
  }

  @Nullable
  public String getSqsInstallationId() {
    return sqsInstallationId;
  }

  @Nullable
  public String getUserUuid() {
    return userUuid;
  }

  @Nullable
  public String getCallingAgentName() {
    var agent = callingAgent.get();
    return agent != null ? agent.name() : null;
  }

  @Nullable
  public String getCallingAgentVersion() {
    var agent = callingAgent.get();
    return agent != null ? agent.version() : null;
  }

}
