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
package org.sonarsource.sonarqube.mcp.harness;

import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueOutputStream extends OutputStream {
  private final BlockingQueue<Integer> blockingQueue;

  public BlockingQueueOutputStream(BlockingQueue<Integer> blockingQueue) {
    this.blockingQueue = blockingQueue;
  }

  @Override
  public void write(final int b) throws InterruptedIOException {
    try {
      blockingQueue.put(b);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      final InterruptedIOException interruptedIoException = new InterruptedIOException();
      interruptedIoException.initCause(e);
      throw interruptedIoException;
    }
  }
}
