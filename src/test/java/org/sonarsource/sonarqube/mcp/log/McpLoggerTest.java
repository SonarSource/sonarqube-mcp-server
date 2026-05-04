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
package org.sonarsource.sonarqube.mcp.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class McpLoggerTest {

  private static final String DEBUG_PROPERTY = "SONARQUBE_DEBUG_ENABLED";

  private Logger mcpLoggerLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    mcpLoggerLogger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpLoggerLogger.addAppender(logAppender);
    mcpLoggerLogger.setLevel(Level.TRACE);
  }

  @AfterEach
  void tearDown() {
    System.clearProperty(DEBUG_PROPERTY);
    if (mcpLoggerLogger != null && logAppender != null) {
      mcpLoggerLogger.detachAppender(logAppender);
      logAppender.stop();
    }
  }

  @Test
  void parameterized_debug_should_format_args_when_debug_enabled() {
    System.setProperty(DEBUG_PROPERTY, "true");

    McpLogger.getInstance().debug("Hello {} from {}", "world", "test");

    assertThat(logAppender.list)
      .singleElement()
      .satisfies(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(event.getFormattedMessage()).isEqualTo("Hello world from test");
      });
  }

  @Test
  void parameterized_debug_should_not_log_when_debug_disabled() {
    System.setProperty(DEBUG_PROPERTY, "false");

    McpLogger.getInstance().debug("Hello {}", "world");

    assertThat(logAppender.list).isEmpty();
  }
}
