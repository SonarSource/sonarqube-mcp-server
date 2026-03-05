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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalyticsServiceTest {

  private AnalyticsClient mockClient;

  @BeforeEach
  void setUp() {
    mockClient = mock(AnalyticsClient.class);
  }

  @Test
  void it_should_build_sqc_event_with_org_uuid() {
    var service = new AnalyticsService(mockClient, "server-id", false, false, true);

    service.notifyToolInvoked("search_issues", "org-uuid-123", null, "user-uuid-456", "cursor", "1.0.0", 123L, true, null, 512L, 1000L);

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.toolName()).isEqualTo("search_issues");
    assertThat(event.connectionType()).isEqualTo("SQC");
    assertThat(event.organizationUuidV4()).isEqualTo("org-uuid-123");
    assertThat(event.sqsInstallationId()).isNull();
    assertThat(event.userUuid()).isEqualTo("user-uuid-456");
    assertThat(event.mcpServerId()).isEqualTo("server-id");
    assertThat(event.transportMode()).isEqualTo("stdio");
    assertThat(event.invocationId()).isNotNull();
    assertThat(event.callingAgentName()).isEqualTo("cursor");
    assertThat(event.callingAgentVersion()).isEqualTo("1.0.0");
    assertThat(event.toolExecutionDurationMs()).isEqualTo(123L);
    assertThat(event.isSuccessful()).isTrue();
    assertThat(event.errorType()).isNull();
    assertThat(event.responseSizeBytes()).isEqualTo(512L);
    assertThat(event.invocationTimestamp()).isEqualTo(1000L);
  }

  @Test
  void it_should_build_sqs_event_with_installation_id() {
    var service = new AnalyticsService(mockClient, "server-id", true, false, false);

    service.notifyToolInvoked("show_rule", null, "install-abc", null, null, null, 42L, false, "not_found", 0L, 2000L);

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.connectionType()).isEqualTo("SQS");
    assertThat(event.organizationUuidV4()).isNull();
    assertThat(event.sqsInstallationId()).isEqualTo("install-abc");
    assertThat(event.userUuid()).isNull();
    assertThat(event.transportMode()).isEqualTo("http");
    assertThat(event.callingAgentName()).isNull();
    assertThat(event.callingAgentVersion()).isNull();
    assertThat(event.toolExecutionDurationMs()).isEqualTo(42L);
    assertThat(event.isSuccessful()).isFalse();
    assertThat(event.errorType()).isEqualTo("not_found");
    assertThat(event.responseSizeBytes()).isZero();
    assertThat(event.invocationTimestamp()).isEqualTo(2000L);
  }

  @Test
  void it_should_ignore_sqs_installation_id_for_sqc_connection() {
    var service = new AnalyticsService(mockClient, "server-id", false, false, true);

    service.notifyToolInvoked("search_issues", "org-uuid", "should-be-ignored", "user-uuid", null, null, 0L, true, null, 0L, 0L);

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.connectionType()).isEqualTo("SQC");
    assertThat(event.sqsInstallationId()).isNull();
    assertThat(event.organizationUuidV4()).isEqualTo("org-uuid");
  }

  @Test
  void it_should_ignore_org_uuid_for_sqs_connection() {
    var service = new AnalyticsService(mockClient, "server-id", false, false, false);

    service.notifyToolInvoked("search_issues", "should-be-ignored", "install-id", null, null, null, 0L, true, null, 0L, 0L);

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.connectionType()).isEqualTo("SQS");
    assertThat(event.organizationUuidV4()).isNull();
    assertThat(event.sqsInstallationId()).isEqualTo("install-id");
  }

  @Test
  void it_should_resolve_transport_mode_as_stdio_when_http_disabled() {
    var service = new AnalyticsService(mockClient, "server-id", false, false, false);
    service.notifyToolInvoked("tool", null, null, null, null, null, 0L, true, null, 0L, 0L);
    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("stdio");
  }

  @Test
  void it_should_resolve_transport_mode_as_http_when_http_enabled_without_tls() {
    var service = new AnalyticsService(mockClient, "server-id", true, false, false);
    service.notifyToolInvoked("tool", null, null, null, null, null, 0L, true, null, 0L, 0L);
    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("http");
  }

  @Test
  void it_should_resolve_transport_mode_as_https_when_http_and_tls_enabled() {
    var service = new AnalyticsService(mockClient, "server-id", true, true, false);
    service.notifyToolInvoked("tool", null, null, null, null, null, 0L, true, null, 0L, 0L);
    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("https");
  }

  @Test
  void it_should_generate_unique_invocation_ids() {
    var service = new AnalyticsService(mockClient, "server-id", false, false, false);

    service.notifyToolInvoked("tool_a", null, null, null, null, null, 0L, true, null, 0L, 0L);
    service.notifyToolInvoked("tool_b", null, null, null, null, null, 0L, true, null, 0L, 0L);

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient, org.mockito.Mockito.times(2)).postEvent(captor.capture());
    var events = captor.getAllValues();

    assertThat(events.get(0).invocationId()).isNotEqualTo(events.get(1).invocationId());
  }

}
