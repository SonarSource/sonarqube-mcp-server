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

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;
import org.sonarsource.sonarqube.mcp.serverapi.users.UsersApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionContextTest {

  @Test
  void empty_returns_all_nulls() {
    var ctx = ConnectionContext.empty();
    assertThat(ctx.getOrganizationUuidV4()).isNull();
    assertThat(ctx.getSqsInstallationId()).isNull();
    assertThat(ctx.getUserUuid()).isNull();
    assertThat(ctx.getCallingAgentName()).isNull();
    assertThat(ctx.getCallingAgentVersion()).isNull();
  }

  @Test
  void captureCallingAgent_sets_name_and_version() {
    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("cursor", "1.0.0");
    assertThat(ctx.getCallingAgentName()).isEqualTo("cursor");
    assertThat(ctx.getCallingAgentVersion()).isEqualTo("1.0.0");
  }

  @Test
  void captureCallingAgent_is_only_set_once() {
    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("cursor", "1.0.0");
    ctx.captureCallingAgent("claude-ai", "2.0.0");
    assertThat(ctx.getCallingAgentName()).isEqualTo("cursor");
    assertThat(ctx.getCallingAgentVersion()).isEqualTo("1.0.0");
  }

  @Test
  void resolveFrom_for_sqc_fetches_org_uuid_and_user_id() {
    var serverApi = mock(ServerApi.class);
    var usersApi = mock(UsersApi.class);
    var orgsApi = mock(OrganizationsApi.class);

    when(serverApi.isSonarQubeCloud()).thenReturn(true);
    when(serverApi.getOrganization()).thenReturn("my-org");
    when(serverApi.usersApi()).thenReturn(usersApi);
    when(serverApi.organizationsApi()).thenReturn(orgsApi);
    when(orgsApi.getOrganizationUuidV4("my-org")).thenReturn("org-uuid-v4");
    when(usersApi.getCurrentUserId()).thenReturn("user-uuid");

    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(serverApi);

    assertThat(ctx.getOrganizationUuidV4()).isEqualTo("org-uuid-v4");
    assertThat(ctx.getSqsInstallationId()).isNull();
    assertThat(ctx.getUserUuid()).isEqualTo("user-uuid");
  }

  @Test
  void resolveFrom_for_sqs_fetches_server_id_and_user_id() {
    var serverApi = mock(ServerApi.class);
    var usersApi = mock(UsersApi.class);
    var systemApi = mock(SystemApi.class);

    when(serverApi.isSonarQubeCloud()).thenReturn(false);
    when(serverApi.usersApi()).thenReturn(usersApi);
    when(serverApi.systemApi()).thenReturn(systemApi);
    when(systemApi.getStatus()).thenReturn(new StatusResponse("server-install-id", "10.0", "UP"));
    when(usersApi.getCurrentUserId()).thenReturn("user-uuid");

    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(serverApi);

    assertThat(ctx.getOrganizationUuidV4()).isNull();
    assertThat(ctx.getSqsInstallationId()).isEqualTo("server-install-id");
    assertThat(ctx.getUserUuid()).isEqualTo("user-uuid");
  }

  @Test
  void resolveFrom_for_sqc_without_org_key_skips_org_uuid_lookup() {
    var serverApi = mock(ServerApi.class);
    var usersApi = mock(UsersApi.class);
    var orgsApi = mock(OrganizationsApi.class);

    when(serverApi.isSonarQubeCloud()).thenReturn(true);
    when(serverApi.getOrganization()).thenReturn(null);
    when(serverApi.usersApi()).thenReturn(usersApi);
    when(serverApi.organizationsApi()).thenReturn(orgsApi);
    when(usersApi.getCurrentUserId()).thenReturn(null);

    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(serverApi);

    assertThat(ctx.getOrganizationUuidV4()).isNull();
    verify(orgsApi, never()).getOrganizationUuidV4(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void resolveFrom_tolerates_null_user_id() {
    var serverApi = mock(ServerApi.class);
    var usersApi = mock(UsersApi.class);
    var systemApi = mock(SystemApi.class);

    when(serverApi.isSonarQubeCloud()).thenReturn(false);
    when(serverApi.usersApi()).thenReturn(usersApi);
    when(serverApi.systemApi()).thenReturn(systemApi);
    when(systemApi.getStatus()).thenReturn(new StatusResponse("server-id", "9.9", "UP"));
    when(usersApi.getCurrentUserId()).thenReturn(null);

    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(serverApi);

    assertThat(ctx.getSqsInstallationId()).isEqualTo("server-id");
    assertThat(ctx.getUserUuid()).isNull();
  }

  @Test
  void resolveFrom_preserves_calling_agent_captured_before_resolution() {
    var serverApi = mock(ServerApi.class);
    var usersApi = mock(UsersApi.class);
    var systemApi = mock(SystemApi.class);

    when(serverApi.isSonarQubeCloud()).thenReturn(false);
    when(serverApi.usersApi()).thenReturn(usersApi);
    when(serverApi.systemApi()).thenReturn(systemApi);
    when(systemApi.getStatus()).thenReturn(new StatusResponse("server-id", "9.9", "UP"));
    when(usersApi.getCurrentUserId()).thenReturn("user-uuid");

    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("cursor", "1.0.0");
    ctx.resolveFrom(serverApi);

    assertThat(ctx.getCallingAgentName()).isEqualTo("cursor");
    assertThat(ctx.getCallingAgentVersion()).isEqualTo("1.0.0");
    assertThat(ctx.getSqsInstallationId()).isEqualTo("server-id");
  }

}
