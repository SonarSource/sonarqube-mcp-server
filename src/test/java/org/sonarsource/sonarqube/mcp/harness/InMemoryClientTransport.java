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

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.sonarsource.sonarqube.mcp.transport.McpJsonMappers.DEFAULT;

/**
 * This class is heavily inspired by the {@link io.modelcontextprotocol.client.transport.StdioClientTransport} class. We just removed the logic to start the server process.
 */
public class InMemoryClientTransport implements McpClientTransport {
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private final Sinks.Many<McpSchema.JSONRPCMessage> inboundSink;
  private final Sinks.Many<McpSchema.JSONRPCMessage> outboundSink;
  private final JacksonMcpJsonMapper mcpJsonMapper = DEFAULT;
  private final Scheduler inboundScheduler;
  private final Scheduler outboundScheduler;
  private final Scheduler errorScheduler;
  private final Sinks.Many<String> errorSink;
  private volatile boolean isClosing;
  private Consumer<String> stdErrorHandler;

  public InMemoryClientTransport(InputStream inputStream, OutputStream outputStream) {
    this.inputStream = inputStream;
    this.outputStream = outputStream;
    this.isClosing = false;
    this.stdErrorHandler = error -> log("STDERR Message received: " + error);
    this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
    this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();
    this.errorSink = Sinks.many().unicast().onBackpressureBuffer();
    this.inboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "inbound");
    this.outboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "outbound");
    this.errorScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "error");
  }

  @Override
  public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    return Mono.fromRunnable(() -> {
      this.handleIncomingMessages(handler);
      this.handleIncomingErrors();
      this.startInboundProcessing();
      this.startOutboundProcessing();
    }).subscribeOn(Schedulers.boundedElastic()).then();
  }

  public void setStdErrorHandler(Consumer<String> errorHandler) {
    this.stdErrorHandler = errorHandler;
  }

  private void handleIncomingMessages(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> inboundMessageHandler) {
    this.inboundSink.asFlux().flatMap(message -> Mono.just(message).transform(inboundMessageHandler).contextWrite(ctx -> ctx.put("observation", "myObservation"))).subscribe();
  }

  private void handleIncomingErrors() {
    this.errorSink.asFlux().subscribe(e -> this.stdErrorHandler.accept(e));
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    return this.outboundSink.tryEmitNext(message).isSuccess() ? Mono.empty() : Mono.error(new RuntimeException("Failed to enqueue message"));
  }

  private void startInboundProcessing() {
    this.inboundScheduler.schedule(() -> {
      try (var processReader = new BufferedReader(new InputStreamReader(inputStream))) {

        String line;
        try {
          while (!this.isClosing && (line = processReader.readLine()) != null) {
            try {
              McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.mcpJsonMapper, line);
              if (!this.inboundSink.tryEmitNext(message).isSuccess()) {
                if (!this.isClosing) {
                  log("Failed to enqueue inbound message: " + message);
                }
                break;
              }
            } catch (Exception e) {
              if (!this.isClosing) {
                log("Error processing inbound message for line: " + line, e);
              }
              break;
            }
          }
        } catch (Throwable var12) {
          try {
            processReader.close();
          } catch (Throwable var10) {
            var12.addSuppressed(var10);
          }

          throw var12;
        }
      } catch (IOException e) {
        if (!this.isClosing) {
          log("Error reading from input stream", e);
        }
      } finally {
        this.isClosing = true;
        this.inboundSink.tryEmitComplete();
      }

    });
  }

  private void startOutboundProcessing() {
    this.handleOutbound(messages -> messages.publishOn(this.outboundScheduler).handle((message, s) -> {
      if (message != null && !this.isClosing) {
        try {
          String jsonMessage = this.mcpJsonMapper.writeValueAsString(message);
          jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
          OutputStream os = this.outputStream;
          synchronized (os) {
            os.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
            os.write("\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
          }

          s.next(message);
        } catch (IOException e) {
          s.error(new RuntimeException(e));
        }
      }

    }));
  }

  protected void handleOutbound(Function<Flux<McpSchema.JSONRPCMessage>, Flux<McpSchema.JSONRPCMessage>> outboundConsumer) {
    ((Flux) outboundConsumer.apply(this.outboundSink.asFlux())).doOnComplete(() -> {
      this.isClosing = true;
      this.outboundSink.tryEmitComplete();
    }).doOnError(e -> {
      if (!this.isClosing) {
        log("Error in outbound processing " + e);
        this.isClosing = true;
        this.outboundSink.tryEmitComplete();
      }

    }).subscribe();
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.fromRunnable(() -> {
      this.isClosing = true;
      log("Initiating graceful shutdown");
    }).then(Mono.defer(() -> {
      this.inboundSink.tryEmitComplete();
      this.outboundSink.tryEmitComplete();
      this.errorSink.tryEmitComplete();
      return Mono.delay(Duration.ofMillis(100L));
    })).then(Mono.defer(() -> {
      log("Closing streams");
      try {
        inputStream.close();
        outputStream.close();
      } catch (Exception e) {
        log("Error during graceful shutdown", e);
      }
      return Mono.empty();
    })).then(Mono.fromRunnable(() -> {
      try {
        this.inboundScheduler.dispose();
        this.errorScheduler.dispose();
        this.outboundScheduler.dispose();
        log("Graceful shutdown completed");
      } catch (Exception e) {
        log("Error during graceful shutdown", e);
      }

    })).then().subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    // Convert TypeRef to TypeReference for Jackson compatibility
    return mcpJsonMapper.getObjectMapper().convertValue(data, mcpJsonMapper.getObjectMapper().getTypeFactory().constructType(typeRef.getType()));
  }

  private static void log(String message) {
    log(message, null);
  }

  private static void log(String message, @Nullable Throwable e) {
    // no log for now
  }
}
