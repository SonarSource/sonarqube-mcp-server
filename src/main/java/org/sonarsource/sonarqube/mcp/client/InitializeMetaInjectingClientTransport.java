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
package org.sonarsource.sonarqube.mcp.client;

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * Decorator around an {@link McpClientTransport} that rewrites outgoing {@code initialize} JSON-RPC requests so that
 * their {@code _meta} field carries startup telemetry correlation data (e.g. {@code mcp_server_id},
 * {@code invocation_id}).
 *
 * <p>The MCP Java SDK's {@code McpSyncClient#initialize()} does not expose
 * (<a href="https://github.com/modelcontextprotocol/java-sdk/issues/940">#940</a>) a way to set {@code _meta} on the
 * {@link McpSchema.InitializeRequest} it builds internally. By wrapping the underlying transport, we can intercept the
 * outgoing message and rebuild it with meta populated — mirroring what {@code ToolExecutor} already does for
 * {@code tools/call}. All other transport responsibilities are delegated unchanged.
 */
public class InitializeMetaInjectingClientTransport implements McpClientTransport {

  private final McpClientTransport delegate;
  private final Map<String, Object> initializeMeta;

  public InitializeMetaInjectingClientTransport(McpClientTransport delegate, Map<String, Object> initializeMeta) {
    this.delegate = delegate;
    this.initializeMeta = initializeMeta;
  }

  @Override
  public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
    return delegate.connect(handler);
  }

  @Override
  public void setExceptionHandler(Consumer<Throwable> handler) {
    delegate.setExceptionHandler(handler);
  }

  @Override
  public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
    return delegate.sendMessage(maybeInjectInitializeMeta(message));
  }

  @Override
  public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
    return delegate.unmarshalFrom(data, typeRef);
  }

  @Override
  public Mono<Void> closeGracefully() {
    return delegate.closeGracefully();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public List<String> protocolVersions() {
    return delegate.protocolVersions();
  }

  McpSchema.JSONRPCMessage maybeInjectInitializeMeta(McpSchema.JSONRPCMessage message) {
    if (message instanceof McpSchema.JSONRPCRequest(String jsonrpc, String method, Object id, Object params)
      && McpSchema.METHOD_INITIALIZE.equals(method)
      && params instanceof McpSchema.InitializeRequest(
      String protocolVersion, McpSchema.ClientCapabilities capabilities,
      McpSchema.Implementation clientInfo, Map<String, Object> existingMeta
    )) {
      var merged = new HashMap<String, Object>();
      if (existingMeta != null) {
        merged.putAll(existingMeta);
      }
      merged.putAll(initializeMeta);
      return new McpSchema.JSONRPCRequest(jsonrpc, method, id,
        new McpSchema.InitializeRequest(protocolVersion, capabilities, clientInfo, merged));
    }
    return message;
  }

}
