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
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import java.util.List;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public final class LogCapture implements AutoCloseable {
  private static final String DEBUG_PROPERTY = "SONARQUBE_DEBUG_ENABLED";
  private final Logger logger = (Logger) LoggerFactory.getLogger(McpLogger.class);
  private final Level previousLevel = logger.getLevel();
  private final String previousDebug = System.getProperty(DEBUG_PROPERTY);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  public LogCapture() {
    this(true);
  }

  public LogCapture(boolean debugEnabled) {
    this(debugEnabled, false);
  }

  public static LogCapture allThreads(boolean debugEnabled) {
    return new LogCapture(debugEnabled, true);
  }

  private LogCapture(boolean debugEnabled, boolean allThreads) {
    if (!allThreads) {
      var capturingThread = Thread.currentThread();
      appender.addFilter(new Filter<>() {
        @Override
        public FilterReply decide(ILoggingEvent event) {
          return Thread.currentThread() == capturingThread ? FilterReply.NEUTRAL : FilterReply.DENY;
        }
      });
    }
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(debugEnabled ? Level.DEBUG : Level.INFO);
    System.setProperty(DEBUG_PROPERTY, Boolean.toString(debugEnabled));
  }

  public List<ILoggingEvent> events() {
    synchronized (appender) {
      return List.copyOf(appender.list);
    }
  }

  public void assertEmpty() {
    assertThat(events()).isEmpty();
  }

  public void assertContains(String message) {
    assertThat(events()).extracting(ILoggingEvent::getFormattedMessage).contains(message);
  }

  public void assertLogged(Level level, String message) {
    assertThat(events()).singleElement().satisfies(event -> {
      assertThat(event.getLevel()).isEqualTo(level);
      assertThat(event.getFormattedMessage()).isEqualTo(message);
      assertThat(event.getThrowableProxy()).isNull();
    });
  }

  @Override
  public void close() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setLevel(previousLevel);
    if (previousDebug == null) {
      System.clearProperty(DEBUG_PROPERTY);
    } else {
      System.setProperty(DEBUG_PROPERTY, previousDebug);
    }
  }
}
