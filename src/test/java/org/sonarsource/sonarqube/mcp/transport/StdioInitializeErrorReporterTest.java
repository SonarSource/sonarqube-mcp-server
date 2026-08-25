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

import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class StdioInitializeErrorReporterTest {

  @Test
  void should_reply_to_initialize_with_jsonrpc_error() {
    var response = respondTo("""
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
      """, "SONARQUBE_TOKEN environment variable or property must be set");

    assertThat(response.id()).isEqualTo(1);
    assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
    assertThat(response.error().message()).contains("SONARQUBE_TOKEN");
  }

  @Test
  void should_skip_non_initialize_lines_then_respond() {
    var response = respondTo("""
      not-json
      {"jsonrpc":"2.0","id":"abc","method":"initialize"}
      """, "auth failed");

    assertThat(response.id()).isEqualTo("abc");
    assertThat(response.error().message()).isEqualTo("auth failed");
  }

  @Test
  void should_preserve_quotes_and_newlines_in_error_message() {
    var response = respondTo("""
      {"jsonrpc":"2.0","id":3,"method":"initialize"}
      """, "Say \"hello\"\nthen reconnect");

    assertThat(response.error().message()).isEqualTo("Say \"hello\"\nthen reconnect");
  }

  @Test
  void should_return_without_output_when_stdin_has_no_initialize() {
    var out = new ByteArrayOutputStream();

    StdioInitializeErrorReporter.respondToInitialize(
      "auth failed",
      stream("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n"),
      out,
      Duration.ofSeconds(2));

    assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void should_return_without_output_when_stdin_is_already_ended() {
    var out = new ByteArrayOutputStream();

    StdioInitializeErrorReporter.respondToInitialize(
      "auth failed",
      new ByteArrayInputStream(new byte[0]),
      out,
      Duration.ofSeconds(2));

    assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void should_use_exception_to_string_when_message_is_blank() {
    var out = new ByteArrayOutputStream();

    StdioInitializeErrorReporter.report(
      new IllegalStateException(""),
      stream("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\"}\n"),
      out);

    var response = parseResponse(out);
    assertThat(response.error().message()).contains("IllegalStateException");
  }

  @Test
  void should_skip_blank_lines_then_respond() {
    var response = respondTo("""
      
      {"jsonrpc":"2.0","id":4,"method":"initialize"}
      """, "missing token");

    assertThat(response.id()).isEqualTo(4);
    assertThat(response.error().message()).isEqualTo("missing token");
  }

  @Test
  void should_complete_when_writing_the_error_fails() {
    var failingOut = new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("broken pipe");
      }
    };

    assertThatCode(() -> StdioInitializeErrorReporter.respondToInitialize(
      "auth failed",
      stream("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}\n"),
      failingOut,
      Duration.ofSeconds(2)))
      .doesNotThrowAnyException();
  }

  @Test
  void should_stop_waiting_after_timeout() {
    var out = new ByteArrayOutputStream();

    StdioInitializeErrorReporter.respondToInitialize(
      "auth failed",
      blockingInput(),
      out,
      Duration.ofMillis(100));

    assertThat(out.size()).isZero();
  }

  @Test
  void should_preserve_interrupt_status_when_waiting_is_interrupted() throws InterruptedException {
    var worker = Thread.ofPlatform().start(() ->
      StdioInitializeErrorReporter.respondToInitialize(
        "auth failed",
        blockingInput(),
        new ByteArrayOutputStream(),
        Duration.ofSeconds(30)));

    Thread.sleep(50);
    worker.interrupt();
    worker.join(Duration.ofSeconds(2));

    assertThat(worker.isAlive()).isFalse();
  }

  private static McpSchema.JSONRPCResponse respondTo(String input, String errorMessage) {
    var out = new ByteArrayOutputStream();
    StdioInitializeErrorReporter.respondToInitialize(errorMessage, stream(input), out, Duration.ofSeconds(2));
    return parseResponse(out);
  }

  private static ByteArrayInputStream stream(String input) {
    return new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
  }

  private static InputStream blockingInput() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        try {
          Thread.sleep(60_000);
          return -1;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("interrupted", e);
        }
      }
    };
  }

  private static McpSchema.JSONRPCResponse parseResponse(ByteArrayOutputStream out) {
    try {
      var parsed = McpSchema.deserializeJsonRpcMessage(McpJsonMappers.DEFAULT, out.toString(StandardCharsets.UTF_8).trim());
      assertThat(parsed).isInstanceOf(McpSchema.JSONRPCResponse.class);
      return (McpSchema.JSONRPCResponse) parsed;
    } catch (IOException e) {
      throw new AssertionError("Failed to parse JSON-RPC response", e);
    }
  }
}
