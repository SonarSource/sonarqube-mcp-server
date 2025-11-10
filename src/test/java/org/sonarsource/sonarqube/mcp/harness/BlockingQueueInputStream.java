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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingQueueInputStream extends InputStream {
  private final BlockingQueue<Integer> blockingQueue;
  private final AtomicBoolean closed = new AtomicBoolean();

  public BlockingQueueInputStream(BlockingQueue<Integer> blockingQueue) {
    this.blockingQueue = blockingQueue;
  }

  @Override
  public int available() throws IOException {
    return blockingQueue.size();
  }

  @Override
  public int read() throws IOException {
    try {
      if (closed.get()) {
        return -1;
      }
      return this.blockingQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while reading from queue", e);
    }
  }

  @Override
  public int read(byte[] bytes, int off, int len) throws IOException {
    if (closed.get()) {
      return -1;
    }
    if (len == 0) {
      return 0;
    }

    // Read the first byte (this will block if queue is empty)
    int firstByte = read();
    if (firstByte == -1) {
      return -1;
    }
    bytes[off] = (byte) firstByte;

    // Read additional bytes if available, but don't block
    int bytesRead = 1;
    for (int i = 1; i < len && !blockingQueue.isEmpty(); i++) {
      int nextByte = read();
      if (nextByte == -1) {
        break;
      }
      bytes[off + i] = (byte) nextByte;
      bytesRead++;
    }

    return bytesRead;
  }

  @Override
  public void close() {
    this.closed.set(true);
    try {
      this.blockingQueue.put(-1);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
