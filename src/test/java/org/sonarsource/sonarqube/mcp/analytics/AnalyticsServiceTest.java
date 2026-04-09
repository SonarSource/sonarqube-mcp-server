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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
    var service = new AnalyticsService(mockClient, "server-id", "1.11.0.14345", false, false, true);

    service.notifyToolInvoked(new ToolInvocationResult("inv-123", "search_issues", "org-uuid-123", null, "user-uuid-456", "cursor", "1.0.0", 123L, true, null, 512L, 1000L));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.toolName()).isEqualTo("search_issues");
    assertThat(event.connectionType()).isEqualTo("SQC");
    assertThat(event.organizationUuidV4()).isEqualTo("org-uuid-123");
    assertThat(event.sqsInstallationId()).isNull();
    assertThat(event.userUuid()).isEqualTo("user-uuid-456");
    assertThat(event.mcpServerId()).isEqualTo("server-id");
    assertThat(event.mcpServerVersion()).isEqualTo("1.11.0.14345");
    assertThat(event.transportMode()).isEqualTo("stdio");
    assertThat(event.invocationId()).isEqualTo("inv-123");
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
    var service = new AnalyticsService(mockClient, "server-id", "1.11.0.14345", true, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "show_rule", null, "install-abc", null, null, null, 42L, false, "not_found", 0L, 2000L));

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
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", false, false, true);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "search_issues", "org-uuid", "should-be-ignored", "user-uuid", null, null, 0L, true, null, 0L, 0L));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.connectionType()).isEqualTo("SQC");
    assertThat(event.sqsInstallationId()).isNull();
    assertThat(event.organizationUuidV4()).isEqualTo("org-uuid");
  }

  @Test
  void it_should_ignore_org_uuid_for_sqs_connection() {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", false, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "search_issues", "should-be-ignored", "install-id", null, null, null, 0L, true, null, 0L, 0L));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    var event = captor.getValue();

    assertThat(event.connectionType()).isEqualTo("SQS");
    assertThat(event.organizationUuidV4()).isNull();
    assertThat(event.sqsInstallationId()).isEqualTo("install-id");
  }

  @Test
  void it_should_resolve_transport_mode_as_stdio_when_http_disabled() {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", false, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "tool", null, null, null, null, null, 0L, true, null, 0L, 0L));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("stdio");
  }

  @Test
  void it_should_resolve_transport_mode_as_http_when_http_enabled_without_tls() {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", true, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "tool", null, null, null, null, null, 0L, true, null, 0L, 0L));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("http");
  }

  @Test
  void it_should_execute_submitted_tasks() throws InterruptedException {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", false, false, false);
    var executed = new AtomicBoolean(false);
    var latch = new CountDownLatch(1);

    service.submit(() -> {
      executed.set(true);
      latch.countDown();
    });

    latch.await();
    assertThat(executed).isTrue();
    service.shutdown();
  }

  @Test
  void it_should_shutdown_gracefully() throws InterruptedException {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", false, false, false);
    var latch = new CountDownLatch(1);

    service.submit(latch::countDown);
    latch.await();
    service.shutdown();

    // A second shutdown on an already-terminated executor must be a no-op
    var threwException = new AtomicBoolean(false);
    try {
      service.shutdown();
    } catch (Exception e) {
      threwException.set(true);
    }
    assertThat(threwException).isFalse();
  }

}
