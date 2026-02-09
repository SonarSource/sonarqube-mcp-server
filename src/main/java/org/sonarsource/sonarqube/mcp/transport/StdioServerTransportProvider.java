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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import io.modelcontextprotocol.util.Assert;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Implementation copied from the Java MCP SDK to workaround this issue: https://github.com/modelcontextprotocol/java-sdk/issues/304
 * Implementation of the MCP Stdio transport provider for servers that communicates using
 * standard input/output streams. Messages are exchanged as newline-delimited JSON-RPC
 * messages over stdin/stdout, with errors and debug information sent to stderr.
 *
 * @author Christian Tzolov
 */
public class StdioServerTransportProvider implements McpServerTransportProvider {

  private static final Logger logger = LoggerFactory.getLogger(StdioServerTransportProvider.class);

  private static final McpJsonMapper jsonMapper = McpJsonMappers.DEFAULT;

  private final InputStream inputStream;

  private final OutputStream outputStream;

  private McpServerSession session;

  private final AtomicBoolean isClosing = new AtomicBoolean(false);

  private final Sinks.One<Void> inboundReady = Sinks.one();

  private final Runnable shutdownCallback;

  private final Duration gracefulShutdownTimeout;

  /**
   * Creates a new StdioServerTransportProvider with the specified ObjectMapper and
   * System streams. Will call shutdown callback when stdin closes (for Docker containers).
   */
  public StdioServerTransportProvider(Runnable shutdownCallback) {
    this(System.in, System.out, shutdownCallback);
  }

  /**
   * Creates a new StdioServerTransportProvider with custom timeout for System streams.
   * Useful for testing to reduce test execution time.
   */
  public StdioServerTransportProvider(@Nullable Runnable shutdownCallback, Duration gracefulShutdownTimeout) {
    this(System.in, System.out, shutdownCallback, gracefulShutdownTimeout);
  }

  /**
   * Creates a new StdioServerTransportProvider with the specified ObjectMapper and
   * streams. Automatically detects if custom streams are used (tests) to disable shutdown callback.
   *
   * @param inputStream  The input stream to read from
   * @param outputStream The output stream to write to
   */
  public StdioServerTransportProvider(InputStream inputStream, OutputStream outputStream) {
    this(inputStream, outputStream, null, Duration.ofSeconds(10));
  }

  private StdioServerTransportProvider(InputStream inputStream, OutputStream outputStream, @Nullable Runnable shutdownCallback) {
    this(inputStream, outputStream, shutdownCallback, Duration.ofSeconds(10));
  }

  private StdioServerTransportProvider(InputStream inputStream, OutputStream outputStream, @Nullable Runnable shutdownCallback, Duration gracefulShutdownTimeout) {
    Assert.notNull(inputStream, "The InputStream can not be null");
    Assert.notNull(outputStream, "The OutputStream can not be null");
    Assert.notNull(gracefulShutdownTimeout, "The gracefulShutdownTimeout can not be null");

    this.inputStream = inputStream;
    this.outputStream = outputStream;
    this.shutdownCallback = shutdownCallback;
    this.gracefulShutdownTimeout = gracefulShutdownTimeout;
  }

  @Override
  public List<String> protocolVersions() {
    return List.of(ProtocolVersions.MCP_2024_11_05);
  }

  @Override
  public void setSessionFactory(McpServerSession.Factory sessionFactory) {
    // Create a single session for the stdio connection
    var transport = new StdioMcpSessionTransport();
    this.session = sessionFactory.create(transport);
    transport.initProcessing();
  }

  @Override
  public Mono<Void> notifyClients(String method, Object params) {
    if (this.session == null) {
      return Mono.error(new McpError("No session to close"));
    }
    return this.session.sendNotification(method, params)
      .doOnError(e -> logger.error("Failed to send notification: {}", e.getMessage()));
  }

  @Override
  public Mono<Void> closeGracefully() {
    if (this.session == null) {
      return Mono.empty();
    }

    return this.session.closeGracefully()
      .timeout(gracefulShutdownTimeout, Mono.fromRunnable(() -> {
        logger.warn("closeGracefully() timed out after {} seconds; proceeding with forced shutdown", gracefulShutdownTimeout.getSeconds());
        try {
          if (this.session != null) {
            this.session.close();
          }
        } catch (Exception e) {
          logger.warn("Forced close encountered error: {}", e.toString());
        }
      }).then())
      .doOnError(e -> logger.warn("closeGracefully() failed: {}", e.toString()))
      .onErrorResume(e -> Mono.empty());
  }

  /**
   * Implementation of McpServerTransport for the stdio session.
   */
  private class StdioMcpSessionTransport implements McpServerTransport {

    private final Sinks.Many<JSONRPCMessage> inboundSink;

    private final Sinks.Many<JSONRPCMessage> outboundSink;

    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    /** Scheduler for handling inbound messages */
    private Scheduler inboundScheduler;

    /** Scheduler for handling outbound messages */
    private Scheduler outboundScheduler;

    private final Sinks.One<Void> outboundReady = Sinks.one();

    public StdioMcpSessionTransport() {

      this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
      this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();

      // Use bounded schedulers for better resource management
      this.inboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(),
        "stdio-inbound");
      this.outboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(),
        "stdio-outbound");
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {

      return Mono.zip(inboundReady.asMono(), outboundReady.asMono()).then(Mono.defer(() -> {
        Sinks.EmitResult emitResult;
        // XXX workaround for https://github.com/modelcontextprotocol/java-sdk/issues/304
        synchronized (StdioMcpSessionTransport.this) {
          emitResult = outboundSink.tryEmitNext(message);
        }
        if (emitResult.isSuccess()) {
          return Mono.empty();
        } else {
          return Mono.error(new RuntimeException("Failed to enqueue message"));
        }
      }));
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      return jsonMapper.convertValue(data, typeRef);
    }

    @Override
    public Mono<Void> closeGracefully() {
      return Mono.fromRunnable(() -> {
        isClosing.set(true);
        logger.debug("Session transport closing gracefully");
        inboundSink.tryEmitComplete();
      });
    }

    @Override
    public void close() {
      isClosing.set(true);
      logger.debug("Session transport closed");
    }

    private void initProcessing() {
      handleIncomingMessages();
      startInboundProcessing();
      startOutboundProcessing();
    }

    private void handleIncomingMessages() {
      this.inboundSink.asFlux().flatMap(message -> session.handle(message)).doOnTerminate(() -> {
        // The outbound processing will dispose its scheduler upon completion
        this.outboundSink.tryEmitComplete();
        this.inboundScheduler.dispose();
      }).subscribe();
    }

    /**
     * Starts the inbound processing thread that reads JSON-RPC messages from stdin.
     * Messages are deserialized and passed to the session for handling.
     */
    private void startInboundProcessing() {
      if (isStarted.compareAndSet(false, true)) {
        this.inboundScheduler.schedule(() -> {
          inboundReady.tryEmitValue(null);
          BufferedReader reader = null;
          try {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            while (!isClosing.get()) {
              try {
                String line = reader.readLine();
                if (line == null || isClosing.get()) {
                  break;
                }

                logger.debug("Received JSON message: {}", line);

                try {
                  McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper,
                    line);
                  if (!this.inboundSink.tryEmitNext(message).isSuccess()) {
                    // logIfNotClosing("Failed to enqueue message");
                    break;
                  }

                } catch (Exception e) {
                  logIfNotClosing("Error processing inbound message", e);
                  break;
                }
              } catch (IOException e) {
                logIfNotClosing("Error reading from stdin", e);
                break;
              }
            }
          } catch (Exception e) {
            logIfNotClosing("Error in inbound processing", e);
          } finally {
            isClosing.set(true);
            if (session != null) {
              session.close();
            }
            inboundSink.tryEmitComplete();

            // Trigger graceful shutdown when stdin closes if environment variable is set (avoid stale containers)
            if (shutdownCallback != null) {
              logger.info("stdin closed (EOF detected) - initiating graceful shutdown");
              try {
                shutdownCallback.run();
              } catch (Exception e) {
                logger.error("Error during graceful shutdown", e);
              }
            }
          }
        });
      }
    }

    /**
     * Starts the outbound processing thread that writes JSON-RPC messages to stdout.
     * Messages are serialized to JSON and written with a newline delimiter.
     */
    private void startOutboundProcessing() {
      Function<Flux<JSONRPCMessage>, Flux<JSONRPCMessage>> outboundConsumer = messages -> messages // @formatter:off
        .doOnSubscribe(subscription -> outboundReady.tryEmitValue(null))
        .publishOn(outboundScheduler)
        .handle((message, sink) -> {
          if (message != null && !isClosing.get()) {
            try {
              String jsonMessage = jsonMapper.writeValueAsString(message);
              // Escape any embedded newlines in the JSON message as per spec
              jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");

              synchronized (outputStream) {
                outputStream.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
                outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
              }
              sink.next(message);
            } catch (IOException e) {
              if (!isClosing.get()) {
                logger.error("Error writing message", e);
                sink.error(new RuntimeException(e));
              } else {
                logger.debug("Stream closed during shutdown", e);
              }
            }
          } else if (isClosing.get()) {
            sink.complete();
          }
        })
        .doOnComplete(() -> {
          isClosing.set(true);
          outboundScheduler.dispose();
        })
        .doOnError(e -> {
          if (!isClosing.get()) {
            logger.error("Error in outbound processing", e);
            isClosing.set(true);
            outboundScheduler.dispose();
          }
        })
        .map(JSONRPCMessage.class::cast);

      outboundConsumer.apply(outboundSink.asFlux()).subscribe();
    } // @formatter:on

    private void logIfNotClosing(String message, Exception e) {
      if (!isClosing.get()) {
        logger.error(message, e);
      }
    }

  }

}
