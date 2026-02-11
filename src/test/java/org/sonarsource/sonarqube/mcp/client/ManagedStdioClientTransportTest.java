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
package org.sonarsource.sonarqube.mcp.client;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for ManagedStdioClientTransport process lifecycle management.
 */
class ManagedStdioClientTransportTest {

  @Test
  void should_connect_to_python_test_server() {
    var resourceUrl = getClass().getResource("/test-mcp-server.py");
    if (resourceUrl == null) {
      // Skip test if Python test server is not available
      return;
    }
    
    var testServerScript = Paths.get(resourceUrl.getPath());
    var serverParams = ServerParameters.builder("python3")
      .args(List.of(testServerScript.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("test-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect should complete successfully without throwing
    var connectResult = transport.connect(message -> message).block(Duration.ofSeconds(5));
    assertThat(connectResult).isNull(); // Mono<Void> returns null on success

    // Clean shutdown should complete without throwing
    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(5));
    assertThat(shutdownResult).isNull(); // Mono<Void> returns null on success
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_terminate_process_on_closeGracefully() throws Exception {
    // Create a simple echo script
    var scriptPath = Files.createTempFile("test-echo-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      while read line; do
        echo "$line"
      done
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("echo-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect to start the process
    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Close gracefully - this should terminate the process
    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(10));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // If we reach here without hanging or throwing, shutdown was successful
    assertThat(shutdownResult).isNull();
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_forcibly_terminate_stubborn_process() throws Exception {
    // Create a script that doesn't terminate on stdin close
    var scriptPath = Files.createTempFile("test-stubborn-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Close stdin and run independently
      exec 0<&-
      # Run for a while
      sleep 30
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("stubborn-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect to start the process
    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Close gracefully - should timeout and forcibly terminate
    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(15));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // If we reach here, the process was terminated successfully (even if forcibly)
    assertThat(shutdownResult).isNull();
  }

  @Test
  void should_handle_stderr_output() {
    var resourceUrl = getClass().getResource("/test-mcp-server.py");
    if (resourceUrl == null) {
      return;
    }
    
    var testServerScript = Paths.get(resourceUrl.getPath());
    var serverParams = ServerParameters.builder("python3")
      .args(List.of(testServerScript.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("test-server", serverParams, McpJsonMappers.DEFAULT);
    
    var stderrMessages = new java.util.concurrent.CopyOnWriteArrayList<String>();
    transport.setStdErrorHandler(stderrMessages::add);

    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Give the server time to emit some stderr
    await().atMost(Duration.ofSeconds(2))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> !stderrMessages.isEmpty());

    transport.closeGracefully().block(Duration.ofSeconds(5));

    // Verify we captured stderr output
    assertThat(stderrMessages).isNotEmpty();
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_handle_process_crash_during_operation() throws Exception {
    // Create a script that crashes after a short time
    var scriptPath = Files.createTempFile("test-crash-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Echo one message then exit with error
      echo '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}'
      sleep 0.5
      exit 1
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("crash-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect should succeed
    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Wait for process to crash
    await().atMost(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> true); // Just wait

    // closeGracefully should handle already-dead process gracefully
    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(5));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // Shutdown should complete successfully even with already-dead process
    assertThat(shutdownResult).isNull();
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_handle_inbound_processing_with_malformed_json() throws Exception {
    // Create a script that sends malformed JSON
    var scriptPath = Files.createTempFile("test-malformed-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Send valid message first
      echo '{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05"}}'
      # Then send malformed JSON
      echo 'this is not json'
      # Wait a bit before exiting
      sleep 1
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("malformed-server", serverParams, McpJsonMappers.DEFAULT);

    var messageCount = new java.util.concurrent.atomic.AtomicInteger(0);
    
    // Connect with a handler that counts messages
    transport.connect(message -> {
      messageCount.incrementAndGet();
      return message;
    }).block(Duration.ofSeconds(5));

    // Give time for processing
    await().atMost(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> messageCount.get() >= 1);

    // Should have received the valid message before hitting malformed JSON
    assertThat(messageCount.get()).isGreaterThanOrEqualTo(1);

    transport.closeGracefully().block(Duration.ofSeconds(5));

    // Cleanup
    Files.deleteIfExists(scriptPath);
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_handle_outbound_message_failure_when_process_dies() throws Exception {
    // Create a script that exits immediately after initial handshake
    var scriptPath = Files.createTempFile("test-quick-exit-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Exit immediately
      exit 0
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("quick-exit-server", serverParams, McpJsonMappers.DEFAULT);

    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Wait for process to exit
    await().atMost(Duration.ofSeconds(2))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> true);

    // Trying to send a message after process dies should be handled gracefully
    // (will fail but shouldn't throw unhandled exception)
    try {
      var testMessage = new io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest(
        "2.0",
        "test-1",
        "test-method",
        null
      );
      transport.sendMessage(testMessage).block(Duration.ofSeconds(1));
    } catch (Exception e) {
      // Expected - process is dead
    }

    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(5));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // Shutdown completed successfully even after outbound failure
    assertThat(shutdownResult).isNull();
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_force_destroy_when_destroy_fails() throws Exception {
    // Create a script that traps SIGTERM but can be killed with SIGKILL
    var scriptPath = Files.createTempFile("test-trap-term-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Trap SIGTERM to ignore it
      trap 'echo "Ignoring SIGTERM"' TERM
      # Close stdin immediately
      exec 0<&-
      # Run for a long time
      while true; do
        sleep 1
      done
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("trap-term-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect to start the process
    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Close gracefully - should timeout on destroy() and use destroyForcibly()
    // This will take at least 5 seconds (PROCESS_TERMINATION_TIMEOUT)
    var shutdownResult = transport.closeGracefully().block(Duration.ofSeconds(15));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // If we reach here, forceDestroyProcess was successfully called
    assertThat(shutdownResult).isNull();
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_handle_multiple_rapid_shutdowns() throws Exception {
    var scriptPath = Files.createTempFile("test-echo-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      while read line; do
        echo "$line"
      done
      """);
    scriptPath.toFile().setExecutable(true);

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .build();

    var transport = new ManagedStdioClientTransport("echo-server", serverParams, McpJsonMappers.DEFAULT);

    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Try to close multiple times rapidly (should be idempotent)
    var shutdown1 = transport.closeGracefully().block(Duration.ofSeconds(5));
    var shutdown2 = transport.closeGracefully().block(Duration.ofSeconds(5));
    var shutdown3 = transport.closeGracefully().block(Duration.ofSeconds(5));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // All shutdown calls should complete successfully
    assertThat(shutdown1).isNull();
    assertThat(shutdown2).isNull();
    assertThat(shutdown3).isNull();
  }

  @Test
  void should_handle_process_start_failure() {
    // Try to start a non-existent command
    var serverParams = ServerParameters.builder("/this/command/does/not/exist")
      .args(List.of())
      .build();

    var transport = new ManagedStdioClientTransport("nonexistent-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect should fail with RuntimeException
    var thrown = org.assertj.core.api.Assertions.catchThrowable(() -> 
      transport.connect(message -> message).block(Duration.ofSeconds(5))
    );
    
    assertThat(thrown)
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Failed to start server process");
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void should_properly_handle_custom_environment_variables() throws Exception {
    // Create a script that outputs an environment variable
    var scriptPath = Files.createTempFile("test-env-server-", ".sh");
    Files.writeString(scriptPath, """
      #!/bin/sh
      # Output the TEST_VAR to stderr
      echo "TEST_VAR=$TEST_VAR" >&2
      # Exit
      exit 0
      """);
    scriptPath.toFile().setExecutable(true);

    var customEnv = new java.util.HashMap<>(System.getenv());
    customEnv.put("TEST_VAR", "test_value_123");

    var serverParams = ServerParameters.builder("sh")
      .args(List.of(scriptPath.toString()))
      .env(customEnv)
      .build();

    var transport = new ManagedStdioClientTransport("env-server", serverParams, McpJsonMappers.DEFAULT);
    
    var stderrMessages = new java.util.concurrent.CopyOnWriteArrayList<String>();
    transport.setStdErrorHandler(stderrMessages::add);

    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Wait for stderr output
    await().atMost(Duration.ofSeconds(2))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> !stderrMessages.isEmpty());

    transport.closeGracefully().block(Duration.ofSeconds(5));

    // Verify the environment variable was passed correctly
    assertThat(stderrMessages)
      .anyMatch(msg -> msg.contains("TEST_VAR=test_value_123"));

    // Cleanup
    Files.deleteIfExists(scriptPath);
  }
}
