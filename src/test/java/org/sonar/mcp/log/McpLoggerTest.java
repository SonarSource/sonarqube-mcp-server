/*
 * Sonar MCP Server
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
package org.sonar.mcp.log;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpLoggerTest {

  @AfterEach
  void teardown() {
    McpLogger.getInstance().setOutput(null);
  }

  @Test
  void it_should_convert_the_log_level() {
    assertThat(McpLogger.toMcpLevel(LogLevel.TRACE)).isEqualTo(McpSchema.LoggingLevel.DEBUG);
    assertThat(McpLogger.toMcpLevel(LogLevel.DEBUG)).isEqualTo(McpSchema.LoggingLevel.DEBUG);
    assertThat(McpLogger.toMcpLevel(LogLevel.INFO)).isEqualTo(McpSchema.LoggingLevel.INFO);
    assertThat(McpLogger.toMcpLevel(LogLevel.WARN)).isEqualTo(McpSchema.LoggingLevel.WARNING);
    assertThat(McpLogger.toMcpLevel(LogLevel.ERROR)).isEqualTo(McpSchema.LoggingLevel.ERROR);
  }

  @Test
  void it_should_send_an_rcp_log_to_the_client() {
    var logger = McpLogger.getInstance();
    var mockServer = mock(McpSyncServer.class);
    logger.setOutput(mockServer);

    logger.log(new LogParams(LogLevel.DEBUG, "Message", null, "stack\ntrace", Instant.now()));

    verify(mockServer).loggingNotification(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.DEBUG, "sonar-mcp-server", "Message"));
    verify(mockServer).loggingNotification(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.DEBUG, "sonar-mcp-server", "stack\ntrace"));
  }

  @Test
  void it_should_send_an_error_log_to_the_client() {
    var logger = McpLogger.getInstance();
    var mockServer = mock(McpSyncServer.class);
    logger.setOutput(mockServer);

    logger.error("Message", new RuntimeException("kaboom"));

    var captor = ArgumentCaptor.forClass(McpSchema.LoggingMessageNotification.class);
    verify(mockServer, times(2)).loggingNotification(captor.capture());
    var notifications = captor.getAllValues();
    assertThat(notifications.getFirst()).isEqualTo(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.ERROR, "sonar-mcp-server", "Message"));
    assertThat(notifications.get(1).level()).isEqualTo(McpSchema.LoggingLevel.ERROR);
    assertThat(notifications.get(1).logger()).isEqualTo("sonar-mcp-server");
    assertThat(notifications.get(1).data()).contains("java.lang.RuntimeException: kaboom\n");
  }

}