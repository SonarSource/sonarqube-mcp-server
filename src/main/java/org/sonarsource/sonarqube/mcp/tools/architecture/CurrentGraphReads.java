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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class CurrentGraphReads {
  private final Semaphore permits = new Semaphore(2);

  <T> T execute(Supplier<T> read) {
    try {
      if (!permits.tryAcquire(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Architecture reads are busy. Try again shortly.");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Architecture read interrupted while waiting for capacity.", interrupted);
    }
    try {
      return read.get();
    } finally {
      permits.release();
    }
  }
}
