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
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Custom MCP client transport that manages the lifecycle of stdio-based proxied servers.
 * This implementation spawns the server process and ensures proper cleanup on shutdown,
 * which is critical for containerized environments to avoid stale containers.
 * 
 * Inspired by the SDK's StdioClientTransport but with explicit process management
 * and configurable termination timeouts.
 */
public class ManagedStdioClientTransport implements McpClientTransport {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofSeconds(5);
  private static final String PROCESS_PREFIX = "Process for '";
  
  private final String serverName;
  private final ServerParameters serverParams;
  private final McpJsonMapper mapper;
  private final Sinks.Many<McpSchema.JSONRPCMessage> inboundSink;
  private final Sinks.Many<McpSchema.JSONRPCMessage> outboundSink;
  private final Sinks.Many<String> errorSink;
  private final Scheduler inboundScheduler;
  private final Scheduler outboundScheduler;
  private final Scheduler errorScheduler;
  
  private volatile boolean isClosing;
  private volatile Process serverProcess;
  private Consumer<String> stdErrorHandler;

  public ManagedStdioClientTransport(String serverName, ServerParameters serverParams, McpJsonMapper mapper) {
    this.serverName = serverName;
    this.serverParams = serverParams;
    this.mapper = mapper;
    this.isClosing = false;
    this.stdErrorHandler = error -> LOG.debug("[" + serverName + "] STDERR: " + error);
    
    this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
    this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();
    this.errorSink = Sinks.many().unicast().onBackpressureBuffer();
    this.inboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), serverName + "-inbound");
    this.outboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), serverName + "-outbound");
    this.errorScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), serverName + "-error");
  }

  public void setStdErrorHandler(Consumer<String> errorHandler) {
    this.stdErrorHandler = errorHandler;
  }

  @Override
  public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    return Mono.fromRunnable(() -> {
      try {
        startServerProcess();
        handleIncomingMessages(handler);
        handleIncomingErrors();
        startInboundProcessing();
        startOutboundProcessing();
        startErrorProcessing();
      } catch (IOException e) {
        throw new RuntimeException("Failed to start server process for '" + serverName + "'", e);
      }
    }).subscribeOn(Schedulers.boundedElastic()).then();
  }

  private void startServerProcess() throws IOException {
    var processBuilder = new ProcessBuilder();
    processBuilder.command().add(serverParams.getCommand());
    processBuilder.command().addAll(serverParams.getArgs());
    
    if (!serverParams.getEnv().isEmpty()) {
      processBuilder.environment().putAll(serverParams.getEnv());
    }
    
    LOG.debug("Starting process for '" + serverName + "': " + processBuilder.command());
    serverProcess = processBuilder.start();
  }

  private void handleIncomingMessages(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    inboundSink.asFlux()
      .flatMap(message -> Mono.just(message).transform(handler))
      .subscribe();
  }

  private void handleIncomingErrors() {
    errorSink.asFlux().subscribe(stdErrorHandler::accept);
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    return outboundSink.tryEmitNext(message).isSuccess() 
      ? Mono.empty() 
      : Mono.error(new RuntimeException("Failed to enqueue message for '" + serverName + "'"));
  }

  private void startInboundProcessing() {
    inboundScheduler.schedule(() -> {
      try (var reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()))) {
        String line;
        while (!isClosing && (line = reader.readLine()) != null) {
          try {
            var message = McpSchema.deserializeJsonRpcMessage(mapper, line);
            if (!inboundSink.tryEmitNext(message).isSuccess()) {
              if (!isClosing) {
                LOG.warn("Failed to enqueue inbound message for '" + serverName + "'");
              }
              break;
            }
          } catch (Exception e) {
            if (!isClosing) {
              LOG.error("Error processing inbound message for '" + serverName + "': " + line, e);
            }
            break;
          }
        }
      } catch (IOException e) {
        if (!isClosing) {
          LOG.error("Error reading from '" + serverName + "' process", e);
        }
      } finally {
        isClosing = true;
        inboundSink.tryEmitComplete();
      }
    });
  }

  private void startOutboundProcessing() {
    handleOutbound(messages -> messages
      .publishOn(outboundScheduler)
      .handle((message, sink) -> {
        if (message != null && !isClosing) {
          try {
            writeMessageToProcess(message);
            sink.next(message);
          } catch (IOException e) {
            sink.error(new RuntimeException("Error writing to '" + serverName + "' process", e));
          }
        }
      }));
  }

  private void writeMessageToProcess(McpSchema.JSONRPCMessage message) throws IOException {
    String jsonMessage = mapper.writeValueAsString(message);
    jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    
    OutputStream os = serverProcess.getOutputStream();
    synchronized (os) {
      os.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
      os.write("\n".getBytes(StandardCharsets.UTF_8));
      os.flush();
    }
  }

  private void handleOutbound(Function<Flux<McpSchema.JSONRPCMessage>, Flux<McpSchema.JSONRPCMessage>> consumer) {
    consumer.apply(outboundSink.asFlux())
      .doOnComplete(() -> {
        isClosing = true;
        outboundSink.tryEmitComplete();
      })
      .doOnError(e -> {
        if (!isClosing) {
          LOG.error("Error in outbound processing for '" + serverName + "'", e);
          isClosing = true;
          outboundSink.tryEmitComplete();
        }
      })
      .subscribe();
  }

  private void startErrorProcessing() {
    errorScheduler.schedule(() -> {
      try (var reader = new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()))) {
        String line;
        while (!isClosing && (line = reader.readLine()) != null) {
          if (!errorSink.tryEmitNext(line).isSuccess()) {
            if (!isClosing) {
              LOG.warn("Failed to enqueue error message for '" + serverName + "'");
            }
            break;
          }
        }
      } catch (IOException e) {
        if (!isClosing) {
          LOG.error("Error reading stderr from '" + serverName + "' process", e);
        }
      } finally {
        errorSink.tryEmitComplete();
      }
    });
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.<Void>fromRunnable(() -> {
      isClosing = true;
      LOG.debug("Initiating graceful shutdown for '" + serverName + "'");
      inboundSink.tryEmitComplete();
      outboundSink.tryEmitComplete();
      errorSink.tryEmitComplete();
    })
    .then(Mono.delay(Duration.ofMillis(100)))
    .then(Mono.<Void>fromRunnable(() -> {
      closeStreams();
      terminateServerProcess();
      disposeSchedulers();
      LOG.info("Graceful shutdown completed for '" + serverName + "'");
    }))
    .onErrorResume(e -> {
      LOG.error("Error during graceful close of '" + serverName + "': " + e.getMessage(), e);
      terminateServerProcess();
      return Mono.empty();
    })
    .subscribeOn(Schedulers.boundedElastic());
  }

  private void closeStreams() {
    if (serverProcess != null) {
      try {
        serverProcess.getInputStream().close();
      } catch (Exception e) {
        LOG.debug("Error closing stdin for '" + serverName + "': " + e.getMessage());
      }
      try {
        serverProcess.getOutputStream().close();
      } catch (Exception e) {
        LOG.debug("Error closing stdout for '" + serverName + "': " + e.getMessage());
      }
      try {
        serverProcess.getErrorStream().close();
      } catch (Exception e) {
        LOG.debug("Error closing stderr for '" + serverName + "': " + e.getMessage());
      }
    }
  }

  private void terminateServerProcess() {
    if (serverProcess == null) {
      LOG.debug("No process to terminate for '" + serverName + "'");
      return;
    }

    if (!serverProcess.isAlive()) {
      LOG.debug(PROCESS_PREFIX + serverName + "' already terminated");
      return;
    }

    try {
      LOG.info("Terminating " + PROCESS_PREFIX + serverName + "'");
      
      // First, try graceful termination
      serverProcess.destroy();

      // Wait for graceful termination with timeout
      boolean terminated = serverProcess.waitFor(
        PROCESS_TERMINATION_TIMEOUT.toMillis(), 
        TimeUnit.MILLISECONDS
      );

      if (!terminated) {
        LOG.warn(PROCESS_PREFIX + serverName + "' did not terminate gracefully within " + 
          PROCESS_TERMINATION_TIMEOUT.getSeconds() + " seconds, forcing termination");
        serverProcess.destroyForcibly();
        
        // Wait a bit for forced termination
        serverProcess.waitFor(2, TimeUnit.SECONDS);
      }

      int exitCode = serverProcess.exitValue();
      LOG.info(PROCESS_PREFIX + serverName + "' terminated with exit code: " + exitCode);
      
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while terminating " + PROCESS_PREFIX + serverName + "', forcing shutdown");
      serverProcess.destroyForcibly();
    } catch (Exception e) {
      LOG.error("Error terminating " + PROCESS_PREFIX + serverName + "': " + e.getMessage(), e);
      forceDestroyProcess();
    }
  }

  private void forceDestroyProcess() {
    if (serverProcess != null) {
      try {
        serverProcess.destroyForcibly();
      } catch (Exception ex) {
        LOG.error("Failed to forcibly destroy process for '" + serverName + "': " + ex.getMessage());
      }
    }
  }

  private void disposeSchedulers() {
    try {
      inboundScheduler.dispose();
      outboundScheduler.dispose();
      errorScheduler.dispose();
    } catch (Exception e) {
      LOG.error("Error disposing schedulers for '" + serverName + "': " + e.getMessage());
    }
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return mapper.convertValue(data, typeRef);
  }
}
