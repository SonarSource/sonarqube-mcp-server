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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogCaptureTest {

  private static final String DEBUG_PROPERTY = "SONARQUBE_DEBUG_ENABLED";
  private static final McpLogger LOG = McpLogger.getInstance();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_ignore_logs_from_other_threads(boolean debugEnabled) throws InterruptedException {
    try (var logs = new LogCapture(debugEnabled)) {
      var background = new Thread(() -> LOG.warn("Background warning"), Thread.currentThread().getName());
      background.start();
      background.join();

      logs.assertEmpty();
      LOG.warn("Captured warning");
      logs.assertLogged(Level.WARN, "Captured warning");
    }
  }

  @Test
  void it_should_capture_logs_from_other_threads_when_requested() throws InterruptedException {
    try (var logs = LogCapture.allThreads(false)) {
      var background = new Thread(() -> LOG.info("Background message"));
      background.start();
      background.join();

      logs.assertContains("Background message");
    }
  }

  @Test
  void it_should_reject_additional_logs_in_strict_assertions() {
    try (var logs = new LogCapture()) {
      LOG.warn("Expected warning");
      LOG.warn("Unexpected warning");

      assertThatThrownBy(() -> logs.assertLogged(Level.WARN, "Expected warning"))
        .isInstanceOf(AssertionError.class);
    }
  }

  @Test
  void it_should_return_an_immutable_snapshot_of_captured_events() {
    try (var logs = new LogCapture()) {
      LOG.info("First message");
      var events = logs.events();
      LOG.warn("Second message");

      assertThat(events).extracting(ILoggingEvent::getFormattedMessage).containsExactly("First message");
      assertThat(logs.events()).extracting(ILoggingEvent::getFormattedMessage).containsExactly("First message", "Second message");
      assertThatThrownBy(events::clear).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_restore_logging_settings_and_stop_capturing_when_closed(boolean debugPropertyWasSet) {
    var logger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    var originalLevel = logger.getLevel();
    var originalDebug = System.getProperty(DEBUG_PROPERTY);
    try {
      logger.setLevel(Level.WARN);
      if (debugPropertyWasSet) {
        System.setProperty(DEBUG_PROPERTY, "false");
      } else {
        System.clearProperty(DEBUG_PROPERTY);
      }
      LogCapture closedCapture;
      try (var logs = new LogCapture()) {
        closedCapture = logs;
        assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
        assertThat(System.getProperty(DEBUG_PROPERTY)).isEqualTo("true");
        LOG.warn("Inside capture");
      }

      assertThat(logger.getLevel()).isEqualTo(Level.WARN);
      assertThat(System.getProperty(DEBUG_PROPERTY)).isEqualTo(debugPropertyWasSet ? "false" : null);
      LOG.warn("After capture");
      closedCapture.assertLogged(Level.WARN, "Inside capture");
    } finally {
      logger.setLevel(originalLevel);
      if (originalDebug == null) {
        System.clearProperty(DEBUG_PROPERTY);
      } else {
        System.setProperty(DEBUG_PROPERTY, originalDebug);
      }
    }
  }
}
