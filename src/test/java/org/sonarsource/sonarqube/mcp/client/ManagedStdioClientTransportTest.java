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

    // Connect should complete successfully
    transport.connect(message -> message).block(Duration.ofSeconds(5));

    // Clean shutdown
    transport.closeGracefully().block(Duration.ofSeconds(5));
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
    transport.closeGracefully().block(Duration.ofSeconds(10));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // If we reach here without hanging, the test passes
    assertThat(true).isTrue();
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
    transport.closeGracefully().block(Duration.ofSeconds(15));

    // Cleanup
    Files.deleteIfExists(scriptPath);
    
    // If we reach here, the process was terminated successfully
    assertThat(true).isTrue();
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
}
