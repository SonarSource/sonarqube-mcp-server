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

import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Stdio launch failures (missing token, URL, storage path, …) otherwise kill the process
 * before MCP {@code initialize}. Waiting for that request and returning a JSON-RPC error
 * lets clients show the real cause instead of Connection closed.
 */
public final class StdioInitializeErrorReporter {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

  private StdioInitializeErrorReporter() {
  }

  public static void report(Throwable error, InputStream in, OutputStream out) {
    var errorMessage = (error.getMessage() == null || error.getMessage().isBlank()) ? error.toString() : error.getMessage();
    if (isInteractiveStdin(in)) {
      return;
    }
    respondToInitialize(errorMessage, in, out, DEFAULT_WAIT);
  }

  @VisibleForTesting
  static void respondToInitialize(String errorMessage, InputStream in, OutputStream out, Duration timeout) {
    var thread = Thread.ofPlatform()
      .name("stdio-initialize-error")
      .daemon()
      .start(() -> {
        try {
          writeErrorForInitialize(errorMessage, in, out);
        } catch (IOException e) {
          LOG.error("Failed to send MCP initialize error to client", e);
        }
      });
    try {
      thread.join(timeout.toMillis());
      if (thread.isAlive()) {
        thread.interrupt();
        LOG.error("Timed out waiting for MCP initialize request after startup failure");
      }
    } catch (InterruptedException e) {
      thread.interrupt();
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while reporting MCP initialize error");
    }
  }

  private static boolean isInteractiveStdin(InputStream in) {
    return in == System.in && System.console() != null;
  }

  private static void writeErrorForInitialize(String errorMessage, InputStream in, OutputStream out) throws IOException {
    var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
      var request = line.isBlank() ? null : parseInitializeRequest(line);
      if (request != null) {
        var error = new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.INTERNAL_ERROR, errorMessage);
        var response = McpSchema.JSONRPCResponse.error(request.id(), error);
        out.write((McpJsonMappers.DEFAULT.writeValueAsString(response) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return;
      }
    }
  }

  private static McpSchema.JSONRPCRequest parseInitializeRequest(String line) {
    try {
      var parsed = McpSchema.deserializeJsonRpcMessage(McpJsonMappers.DEFAULT, line);
      if (parsed instanceof McpSchema.JSONRPCRequest request && McpSchema.METHOD_INITIALIZE.equals(request.method())) {
        return request;
      }
    } catch (IOException | IllegalArgumentException e) {
      LOG.debug("Ignoring non-JSON-RPC line while reporting startup failure: {}", e.getMessage());
    }
    return null;
  }
}
