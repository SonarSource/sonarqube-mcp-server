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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

  private AnalyticsClient mockClient;

  @BeforeEach
  void setUp() {
    mockClient = mock(AnalyticsClient.class);
  }

  @Test
  void it_should_build_sqc_event_with_org_uuid() {
    var service = new AnalyticsService(mockClient, "server-id", "1.11.0.14345", false, false, true);

    service.notifyToolInvoked(new ToolInvocationResult("inv-123", "search_issues", "org-uuid-123", null, "user-uuid-456", "cursor", "1.0.0", 123L, true, null, 512L, 1000L,
      List.of("issues")));

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
    assertThat(event.matchingToolsets()).containsExactly("issues");
  }

  @Test
  void it_should_build_sqs_event_with_installation_id() {
    var service = new AnalyticsService(mockClient, "server-id", "1.11.0.14345", true, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "show_rule", null, "install-abc", null, null, null, 42L, false, "not_found", 0L, 2000L,
      List.of("rules")));

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

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "search_issues", "org-uuid", "should-be-ignored", "user-uuid", null, null, 0L, true, null, 0L, 0L,
      List.of("issues")));

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

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "search_issues", "should-be-ignored", "install-id", null, null, null, 0L, true, null, 0L, 0L,
      List.of("issues")));

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

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "tool", null, null, null, null, null, 0L, true, null, 0L, 0L, List.of()));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("stdio");
  }

  @Test
  void it_should_resolve_transport_mode_as_http_when_http_enabled_without_tls() {
    var service = new AnalyticsService(mockClient, "server-id", "1.0.0", true, false, false);

    service.notifyToolInvoked(new ToolInvocationResult("inv-id", "tool", null, null, null, null, null, 0L, true, null, 0L, 0L, List.of()));

    var captor = ArgumentCaptor.forClass(McpToolInvokedEvent.class);
    verify(mockClient).postEvent(captor.capture());
    assertThat(captor.getValue().transportMode()).isEqualTo("http");
  }

  @Test
  void it_should_send_initialization_with_identity_and_authentication_without_identity() {
    var service = new AnalyticsService(mockClient, "server", "1", true, false, true);
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.usersApi().getCurrentUserId()).thenReturn("user");
    service.notifyClientInitialized(new HttpAnalyticsContext("oauth", "key"), " Custom Client ", "1.2+dev", 123, () -> api);
    service.notifyOAuthAuthenticationCompleted(new OAuthAuthenticationResult(124, 20, "rejected", "delegation_validation", "insufficient_scope", "key"));
    service.shutdown();
    var events = ArgumentCaptor.forClass(AnalyticsEvent.class);
    verify(mockClient, times(2)).postEvent(events.capture());
    assertThat(events.getAllValues().getFirst()).isInstanceOfSatisfying(McpClientInitializedEvent.class, event -> {
      assertThat(event.callingAgentName()).isEqualTo(" Custom Client ");
      assertThat(event.callingAgentVersion()).isEqualTo("1.2+dev");
      assertThat(event.oauthClientKey()).isEqualTo("key");
      assertThat(event.authenticationMethod()).isEqualTo("oauth");
      assertThat(event.initializationTimestamp()).isEqualTo(123);
      assertThat(event.userUuid()).isEqualTo("user");
      assertThat(event.sqsInstallationId()).isNull();
      assertThat(event.connectionType()).isEqualTo("SQC");
    });
    assertThat(events.getAllValues().getLast()).isInstanceOfSatisfying(McpOAuthAuthenticationCompletedEvent.class, event -> {
      assertThat(event.failureStage()).isEqualTo("delegation_validation");
      assertThat(event.authenticationDurationMs()).isEqualTo(20);
      assertThat(event.outcome()).isEqualTo("rejected");
      assertThat(event.failureCategory()).isEqualTo("insufficient_scope");
      assertThat(event.authenticationTimestamp()).isEqualTo(124);
    });
    verify(api.usersApi()).getCurrentUserId();
    verify(api, never()).systemApi();
    verify(api, never()).organizationsApi();
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void it_should_enrich_server_identifiers_independently(boolean userAvailable, boolean installationAvailable) {
    var service = new AnalyticsService(mockClient, "server", "1", true, false, false);
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    if (userAvailable) {
      when(api.usersApi().getCurrentUserId()).thenReturn("user");
    } else {
      when(api.usersApi().getCurrentUserId()).thenThrow(new IllegalStateException("private"));
    }
    if (installationAvailable) {
      when(api.systemApi().getStatus().id()).thenReturn("installation");
    } else {
      when(api.systemApi().getStatus()).thenThrow(new IllegalStateException("private"));
    }
    service.notifyClientInitialized(new HttpAnalyticsContext("token", null), "client", "1", 123, () -> api);
    service.shutdown();
    var event = ArgumentCaptor.forClass(McpClientInitializedEvent.class);
    verify(mockClient).postEvent(event.capture());
    assertThat(event.getValue().userUuid()).isEqualTo(userAvailable ? "user" : null);
    assertThat(event.getValue().sqsInstallationId()).isEqualTo(installationAvailable ? "installation" : null);
    verify(api, never()).organizationsApi();
  }

  @Test
  void it_should_keep_initialization_when_its_api_factory_fails() {
    var service = new AnalyticsService(mockClient, "server", "1", true, false, true);
    service.notifyClientInitialized(new HttpAnalyticsContext("oauth", "key"), "client", "1", 123,
      () -> { throw new IllegalStateException("private"); });
    service.shutdown();
    var event = ArgumentCaptor.forClass(McpClientInitializedEvent.class);
    verify(mockClient).postEvent(event.capture());
    assertThat(event.getValue().userUuid()).isNull();
    assertThat(event.getValue().oauthClientKey()).isEqualTo("key");
  }

  @Test
  void it_should_run_initialization_identity_lookup_off_the_request_thread() throws Exception {
    var service = new AnalyticsService(mockClient, "server", "1", true, false, true);
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var requestThread = Thread.currentThread();
    when(api.usersApi().getCurrentUserId()).thenAnswer(invocation -> {
      assertThat(Thread.currentThread()).isNotSameAs(requestThread);
      started.countDown();
      assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
      return "user";
    });
    try {
      service.notifyClientInitialized(new HttpAnalyticsContext("oauth", "key"), "client", "1", 123, () -> api);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      verifyNoInteractions(mockClient);
    } finally {
      release.countDown();
      service.shutdown();
    }
    var event = ArgumentCaptor.forClass(McpClientInitializedEvent.class);
    verify(mockClient).postEvent(event.capture());
    assertThat(event.getValue().userUuid()).isEqualTo("user");
  }

  @Test
  void it_should_drop_excess_work_without_blocking_or_failing_the_caller() throws Exception {
    var service = new AnalyticsService(mockClient, "server", "1", true, false, true);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var completed = new AtomicInteger();
    service.submit(() -> {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatCode(() -> {
        for (var i = 0; i < 1002; i++) {
          service.submit(completed::incrementAndGet);
        }
      }).doesNotThrowAnyException();
    } finally {
      release.countDown();
      service.shutdown();
    }
    assertThat(completed.get()).isEqualTo(1000);
    assertThatCode(() -> service.notifyOAuthAuthenticationCompleted(new OAuthAuthenticationResult(1, 1, "success", null, null, null)))
      .doesNotThrowAnyException();
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
