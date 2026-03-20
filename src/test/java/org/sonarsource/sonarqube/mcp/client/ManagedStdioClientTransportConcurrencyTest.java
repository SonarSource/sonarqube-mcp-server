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
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the concurrent sendMessage bug in ManagedStdioClientTransport.
 *
 * <p>When two threads call sendMessage simultaneously on the same transport,
 * the unicast Reactor sink rejects the second emit with FAIL_NON_SERIALIZED,
 * surfacing as "Failed to enqueue message for 'sonar-cag'".
 *
 * <p>This matches the production failure observed when an MCP client sends two
 * parallel tool calls (e.g., get_current_architecture with depth=0 and depth=1),
 * which are dispatched on separate boundedElastic threads that race on the same transport.
 */
class ManagedStdioClientTransportConcurrencyTest {

  private ManagedStdioClientTransport transport;

  @AfterEach
  void tearDown() {
    if (transport != null) {
      transport.closeGracefully().block(Duration.ofSeconds(5));
    }
  }

  /**
   * Reproduces the exact production scenario: two concurrent tool calls through
   * the same transport. The unicast sink's CAS-based serialization guard causes
   * one of the two tryEmitNext calls to return FAIL_NON_SERIALIZED.
   *
   * Repeated 20 times because the race is timing-dependent.
   */
  @RepeatedTest(20)
  void concurrent_sendMessage_should_not_fail() throws Exception {
    // Use 'cat' as a simple process that keeps stdin/stdout open
    var serverParams = ServerParameters.builder("cat")
      .env(Map.of())
      .build();
    transport = new ManagedStdioClientTransport("test-server", serverParams, McpJsonMappers.DEFAULT);

    // Connect the transport (starts process, sets up sinks and processing threads)
    transport.connect(mono -> mono.flatMap(msg -> Mono.empty())).block(Duration.ofSeconds(5));

    // Two JSON-RPC messages mimicking two concurrent tool calls
    var msg1 = new McpSchema.JSONRPCRequest("2.0", "tools/call", "1",
      Map.of("name", "get_current_architecture", "arguments", Map.of("depth", 0)));
    var msg2 = new McpSchema.JSONRPCRequest("2.0", "tools/call", "2",
      Map.of("name", "get_current_architecture", "arguments", Map.of("depth", 1)));

    // CyclicBarrier maximizes chance of concurrent execution
    var barrier = new CyclicBarrier(2);
    var errors = new CopyOnWriteArrayList<Throwable>();
    var latch = new CountDownLatch(2);

    Runnable sender1 = () -> {
      try {
        barrier.await(2, TimeUnit.SECONDS);
        transport.sendMessage(msg1).block(Duration.ofSeconds(2));
      } catch (Exception e) {
        errors.add(e);
      } finally {
        latch.countDown();
      }
    };

    Runnable sender2 = () -> {
      try {
        barrier.await(2, TimeUnit.SECONDS);
        transport.sendMessage(msg2).block(Duration.ofSeconds(2));
      } catch (Exception e) {
        errors.add(e);
      } finally {
        latch.countDown();
      }
    };

    var t1 = new Thread(sender1, "boundedElastic-5");
    var t2 = new Thread(sender2, "boundedElastic-6");
    t1.start();
    t2.start();

    latch.await(5, TimeUnit.SECONDS);

    // With the unicast sink, one send fails with "Failed to enqueue message"
    // due to FAIL_NON_SERIALIZED when the two tryEmitNext calls overlap.
    assertThat(errors)
      .as("Both concurrent sendMessage calls should succeed, but the unicast sink " +
        "rejects one with FAIL_NON_SERIALIZED when two threads race on tryEmitNext")
      .isEmpty();
  }
}
