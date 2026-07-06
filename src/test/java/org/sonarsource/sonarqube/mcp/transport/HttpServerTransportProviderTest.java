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
package org.sonarsource.sonarqube.mcp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

class HttpServerTransportProviderTest {

  private ListAppender<ILoggingEvent> logAppender;
  private Logger mcpLogger;

  @BeforeEach
  void setUp() {
    mcpLogger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    mcpLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  void should_not_warn_about_all_interfaces_when_running_in_container() {
    new HttpServerTransportProvider(8080, "0.0.0.0", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", true);

    assertThat(logAppender.list)
      .filteredOn(event -> event.getLevel() == Level.WARN)
      .extracting(ILoggingEvent::getFormattedMessage)
      .noneMatch(msg -> msg.contains("all network interfaces"));
  }

  @Test
  void should_warn_about_all_interfaces_when_not_running_in_container() {
    new HttpServerTransportProvider(8080, "0.0.0.0", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", false);

    assertThat(logAppender.list)
      .filteredOn(event -> event.getLevel() == Level.WARN)
      .extracting(ILoggingEvent::getFormattedMessage)
      .anyMatch(msg -> msg.contains("all network interfaces"));
  }
}
