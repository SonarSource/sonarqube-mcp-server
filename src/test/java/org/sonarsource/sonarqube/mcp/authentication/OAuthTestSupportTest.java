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
package org.sonarsource.sonarqube.mcp.authentication;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.LogCapture;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTestSupportTest {
  private static final McpLogger LOG = McpLogger.getInstance();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_ignore_logs_from_other_threads(boolean debugEnabled) throws InterruptedException {
    try (var logs = new LogCapture(debugEnabled)) {
      var background = new Thread(() -> LOG.warn("Unrelated background log"), Thread.currentThread().getName());
      background.start();
      background.join();

      logs.assertEmpty();
      LOG.warn("Auth0 token exchange unavailable (SocketTimeoutException)");
      logs.assertLogged(Level.WARN, "Auth0 token exchange unavailable (SocketTimeoutException)");
    }
  }

  @Test
  void it_should_capture_logs_from_other_threads_when_requested() throws InterruptedException {
    try (var logs = LogCapture.allThreads(false)) {
      var background = new Thread(() -> LOG.info("Transport: HTTP"));
      background.start();
      background.join();

      logs.assertContains("Transport: HTTP");
    }
  }

  @Test
  void it_should_reject_additional_logs_from_the_capturing_thread() {
    try (var logs = new LogCapture()) {
      LOG.warn("Auth0 token exchange unavailable (SocketTimeoutException)");
      LOG.warn("private-token-or-secret");

      assertThatThrownBy(() -> logs.assertLogged(Level.WARN, "Auth0 token exchange unavailable (SocketTimeoutException)"))
        .isInstanceOf(AssertionError.class);
    }
  }
}
