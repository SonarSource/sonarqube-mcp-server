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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;
import org.sonarsource.sonarqube.mcp.serverapi.users.UsersApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

  @Test
  void it_should_cache_selected_organizations_without_changing_default_or_identity() {
    var defaultApi = cloudApi("default-org", "default-uuid");
    var selectedApi = cloudApi("selected-org", "selected-uuid");
    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("agent", "1.0");
    ctx.resolveFrom(defaultApi);
    var captured = ctx.snapshot();

    var selected = ctx.snapshotFor(selectedApi, captured);
    assertThat(selected).isEqualTo(new ConnectionContext.Snapshot("selected-uuid", null, "user-uuid", "agent", "1.0"));
    assertThat(ctx.snapshotFor(selectedApi, captured)).isEqualTo(selected);
    assertThat(ctx.snapshotFor(defaultApi, captured)).isEqualTo(captured);
    assertThat(ctx.snapshot()).isEqualTo(captured);
    verify(defaultApi.organizationsApi()).getOrganizationUuidV4("default-org");
    verify(defaultApi.usersApi()).getCurrentUserId();
    verify(selectedApi.organizationsApi()).getOrganizationUuidV4("selected-org");
    verify(selectedApi.usersApi(), never()).getCurrentUserId();
  }

  @Test
  void it_should_retry_missing_and_failed_selected_organization_lookups() {
    var ctx = ConnectionContext.empty();
    var api = cloudApi("selected-org", null);
    when(api.organizationsApi().getOrganizationUuidV4("selected-org"))
      .thenReturn(null).thenThrow(new IllegalStateException("lookup failed")).thenReturn("selected-uuid");
    var captured = ctx.snapshot();

    assertThat(ctx.snapshotFor(api, captured).organizationUuidV4()).isNull();
    assertThatThrownBy(() -> ctx.snapshotFor(api, captured)).hasMessage("lookup failed");
    assertThat(ctx.snapshotFor(api, captured).organizationUuidV4()).isEqualTo("selected-uuid");
    assertThat(ctx.snapshotFor(api, captured).organizationUuidV4()).isEqualTo("selected-uuid");
    assertThat(ctx.snapshot()).isEqualTo(captured);
    verify(api.organizationsApi(), times(3)).getOrganizationUuidV4("selected-org");
  }

  @Test
  void it_should_retry_missing_startup_organization_without_mutating_the_default_snapshot() {
    var api = cloudApi("default-org", null);
    when(api.organizationsApi().getOrganizationUuidV4("default-org")).thenReturn(null, "default-uuid");
    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(api);
    var captured = ctx.snapshot();

    assertThat(captured.organizationUuidV4()).isNull();
    assertThat(ctx.snapshotFor(api, captured).organizationUuidV4()).isEqualTo("default-uuid");
    assertThat(ctx.snapshotFor(api, captured).organizationUuidV4()).isEqualTo("default-uuid");
    assertThat(ctx.snapshot()).isEqualTo(captured);
    verify(api.organizationsApi(), times(2)).getOrganizationUuidV4("default-org");
    verify(api.usersApi()).getCurrentUserId();
  }

  @Test
  void it_should_share_successful_organization_lookups_between_concurrent_invocations() {
    var ctx = ConnectionContext.empty();
    var captured = ctx.snapshot();
    var api = cloudApi("org", "org-uuid");
    try (var executor = Executors.newFixedThreadPool(4)) {
      var results = IntStream.range(0, 16)
        .mapToObj(ignored -> CompletableFuture.supplyAsync(() -> ctx.snapshotFor(api, captured), executor))
        .toList();

      assertThat(results).isNotEmpty().allSatisfy(result -> assertThat(result.join().organizationUuidV4()).isEqualTo("org-uuid"));
    }
    verify(api.organizationsApi()).getOrganizationUuidV4("org");
    verify(api.usersApi(), never()).getCurrentUserId();
  }

  @Test
  void it_should_reuse_server_identity_in_invocation_snapshots() {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.systemApi().getStatus()).thenReturn(new StatusResponse("server-id", "10.0", "UP"));
    when(api.usersApi().getCurrentUserId()).thenReturn("user-uuid");
    var ctx = ConnectionContext.empty();
    ctx.resolveFrom(api);
    ctx.captureCallingAgent("agent", "1.0");
    var captured = ctx.snapshot();

    assertThat(ctx.snapshotFor(api, captured)).isEqualTo(new ConnectionContext.Snapshot(null, "server-id", "user-uuid", "agent", "1.0"));
    assertThat(ctx.snapshotFor(api, captured)).isEqualTo(captured);
    verify(api.systemApi()).getStatus();
    verify(api.usersApi()).getCurrentUserId();
    verify(api.organizationsApi(), never()).getOrganizationUuidV4(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void it_should_keep_snapshots_immutable_after_context_resolution_and_agent_capture() {
    var ctx = ConnectionContext.empty();
    var captured = ctx.snapshot();

    ctx.resolveFrom(cloudApi("org", "org-uuid"));
    ctx.captureCallingAgent("agent", "1.0");

    assertThat(captured).isEqualTo(new ConnectionContext.Snapshot(null, null, null, null, null));
    assertThat(ctx.snapshot()).isEqualTo(new ConnectionContext.Snapshot("org-uuid", null, "user-uuid", "agent", "1.0"));
  }

  private static ServerApi cloudApi(String organization, String uuid) {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.isSonarQubeCloud()).thenReturn(true);
    when(api.getOrganization()).thenReturn(organization);
    when(api.organizationsApi().getOrganizationUuidV4(organization)).thenReturn(uuid);
    when(api.usersApi().getCurrentUserId()).thenReturn("user-uuid");
    return api;
  }

}
