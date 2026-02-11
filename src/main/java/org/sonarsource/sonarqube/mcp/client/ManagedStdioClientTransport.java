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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Extended implementation of the MCP Stdio client transport that ensures proper process
 * lifecycle management for proxied MCP servers.
 * 
 * <p>This implementation is based on the SDK's {@code StdioClientTransport} but adds
 * explicit process termination with configurable timeouts to ensure child processes
 * are properly cleaned up when the parent container shuts down.
 * 
 * <p>The SDK's implementation uses {@code process.destroy()} and waits for exit, but
 * doesn't handle cases where processes don't respond to SIGTERM. This implementation
 * adds a timeout and fallback to {@code destroyForcibly()} to guarantee cleanup.
 * 
 * @see io.modelcontextprotocol.client.transport.StdioClientTransport
 */
public class ManagedStdioClientTransport implements McpClientTransport {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofSeconds(5);
  
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
  private Process process;
  private Consumer<String> stdErrorHandler;

  public ManagedStdioClientTransport(String serverName, ServerParameters serverParams, McpJsonMapper mapper) {
    this.serverName = serverName;
    this.serverParams = serverParams;
    this.mapper = mapper;
    this.isClosing = false;
    this.stdErrorHandler = error -> LOG.info("[" + serverName + "] STDERR: " + error);
    
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
    return Mono.<Void>fromRunnable(() -> {
      LOG.info("MCP server '" + serverName + "' starting");
      handleIncomingMessages(handler);
      handleIncomingErrors();

      // Build command list
      var fullCommand = new java.util.ArrayList<String>();
      fullCommand.add(serverParams.getCommand());
      fullCommand.addAll(serverParams.getArgs());

      // Start the process
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.command(fullCommand);
      processBuilder.environment().putAll(serverParams.getEnv());

      try {
        this.process = processBuilder.start();
      } catch (IOException e) {
        throw new RuntimeException("Failed to start process with command: " + fullCommand, e);
      }

      // Validate process streams
      if (this.process.getInputStream() == null || this.process.getOutputStream() == null) {
        this.process.destroy();
        throw new RuntimeException("Process input or output stream is null");
      }

      // Start processing threads
      startInboundProcessing();
      startOutboundProcessing();
      startErrorProcessing();
      LOG.info("MCP server '" + serverName + "' started");
    }).subscribeOn(Schedulers.boundedElastic());
  }

  private void handleIncomingMessages(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    inboundSink.asFlux()
      .flatMap(message -> Mono.just(message)
        .transform(handler)
        .contextWrite(ctx -> ctx.put("observation", "myObservation")))
      .subscribe();
  }

  private void handleIncomingErrors() {
    errorSink.asFlux().subscribe(stdErrorHandler);
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    return outboundSink.tryEmitNext(message).isSuccess() 
      ? Mono.empty() 
      : Mono.error(new RuntimeException("Failed to enqueue message for '" + serverName + "'"));
  }

  private void startInboundProcessing() {
    inboundScheduler.schedule(() -> {
      try (BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while (!isClosing && (line = processReader.readLine()) != null) {
          try {
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(mapper, line);
            if (!inboundSink.tryEmitNext(message).isSuccess()) {
              if (!isClosing) {
                LOG.error("Failed to enqueue inbound message: " + message);
              }
              break;
            }
          } catch (Exception e) {
            if (!isClosing) {
              LOG.error("Error processing inbound message for line: " + line, e);
            }
            break;
          }
        }
      } catch (IOException e) {
        if (!isClosing) {
          LOG.error("Error reading from input stream", e);
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
    // Escape any embedded newlines in the JSON message as per spec:
    // https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio
    // - Messages are delimited by newlines, and MUST NOT contain embedded newlines.
    jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    
    var os = process.getOutputStream();
    synchronized (os) {
      os.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
      os.write("\n".getBytes(StandardCharsets.UTF_8));
      os.flush();
    }
  }

  protected void handleOutbound(Function<Flux<McpSchema.JSONRPCMessage>, Flux<McpSchema.JSONRPCMessage>> consumer) {
    consumer.apply(outboundSink.asFlux())
      .doOnComplete(() -> {
        isClosing = true;
        outboundSink.tryEmitComplete();
      })
      .doOnError(e -> {
        if (!isClosing) {
          isClosing = true;
          outboundSink.tryEmitComplete();
        }
      })
      .subscribe();
  }

  private void startErrorProcessing() {
    errorScheduler.schedule(() -> {
      try (BufferedReader processErrorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String line;
        while (!isClosing && (line = processErrorReader.readLine()) != null) {
          try {
            if (!errorSink.tryEmitNext(line).isSuccess()) {
              if (!isClosing) {
                LOG.error("Failed to emit error message");
              }
              break;
            }
          } catch (Exception e) {
            break;
          }
        }
      } catch (IOException e) {
        if (!isClosing) {
          LOG.error("Error reading from error stream", e);
        }
      } finally {
        isClosing = true;
        errorSink.tryEmitComplete();
      }
    });
  }

  /**
   * Gracefully closes the transport by terminating the process and cleaning up resources.
   * 
   * <p>This method extends the SDK's approach by adding a timeout-based termination:
   * <ol>
   *   <li>Complete all sinks to stop accepting new messages</li>
   *   <li>Wait 100ms for pending messages to process</li>
   *   <li>Send SIGTERM via {@code process.destroy()}</li>
   *   <li>Wait up to 5 seconds for graceful termination</li>
   *   <li>If timeout expires, force termination with {@code destroyForcibly()}</li>
   *   <li>Dispose all schedulers</li>
   * </ol>
   * 
   * @return A Mono that completes when the transport is closed
   */
  @Override
  public Mono<Void> closeGracefully() {
    return Mono.fromRunnable(() -> {
      isClosing = true;
      LOG.debug("Initiating graceful shutdown");
    })
    .then(Mono.<Void>defer(() -> {
      // First complete all sinks to stop accepting new messages
      inboundSink.tryEmitComplete();
      outboundSink.tryEmitComplete();
      errorSink.tryEmitComplete();

      // Give a short time for any pending messages to be processed
      return Mono.delay(Duration.ofMillis(100)).then();
    }))
    .then(Mono.defer(() -> {
      LOG.debug("Sending TERM to process");
      if (this.process != null) {
        this.process.destroy();
        
        // Wait for graceful termination with timeout
        return Mono.fromFuture(process.onExit())
          .timeout(PROCESS_TERMINATION_TIMEOUT, Mono.fromRunnable(() -> {
            LOG.warn("Process '" + serverName + "' did not terminate gracefully within " + 
              PROCESS_TERMINATION_TIMEOUT.getSeconds() + " seconds, forcing termination");
            this.process.destroyForcibly();
          }).then(Mono.fromFuture(process.onExit())))
          .onErrorResume(e -> {
            // If anything goes wrong, force destroy
            if (this.process != null && this.process.isAlive()) {
              this.process.destroyForcibly();
            }
            return Mono.empty();
          });
      } else {
        LOG.warn("Process not started");
        return Mono.empty();
      }
    }))
    .doOnNext(proc -> {
      if (proc.exitValue() != 0) {
        LOG.warn("Process '" + serverName + "' terminated with code " + proc.exitValue());
      } else {
        LOG.info("MCP server process '" + serverName + "' stopped");
      }
    })
    .onErrorResume(InterruptedException.class, e -> {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while terminating process '" + serverName + "', forcing shutdown");
      if (this.process != null) {
        this.process.destroyForcibly();
      }
      return Mono.empty();
    })
    .then(Mono.fromRunnable(() -> {
      try {
        // The Threads are blocked on readLine so disposeGracefully would not
        // interrupt them, therefore we issue an async hard dispose.
        inboundScheduler.dispose();
        errorScheduler.dispose();
        outboundScheduler.dispose();

        LOG.debug("Graceful shutdown completed");
      } catch (Exception e) {
        LOG.error("Error during graceful shutdown", e);
      }
    }))
    .then()
    .subscribeOn(Schedulers.boundedElastic());
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return mapper.convertValue(data, typeRef);
  }
}
