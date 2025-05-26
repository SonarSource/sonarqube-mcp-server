/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.mcp.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonar.mcp.SonarMcpServer;

public class SonarMcpServerTestHarness extends TypeBasedParameterResolver<SonarMcpServerTestHarness> implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {
  private static final Map<String, String> DEFAULT_ENV = Map.of(
    "STORAGE_PATH", "",
    "SONARQUBE_CLOUD_URL", "fake.url");
  private boolean isStatic;
  private final List<McpSyncClient> clients = new ArrayList<>();

  @Override
  public SonarMcpServerTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    isStatic = true;
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (isStatic) {
      shutdownAll();
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (!isStatic) {
      shutdownAll();
    }
  }

  private void shutdownAll() {
    clients.forEach(McpSyncClient::closeGracefully);
    clients.clear();
  }

  public McpSyncClient newClient() {
    return newClient(Map.of());
  }

  public McpSyncClient newClient(Map<String, String> overriddenEnv) {
    McpSyncClient client;
    try {
      var clientToServerOutputStream = new PipedOutputStream();
      var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
      var environment = new HashMap<>(DEFAULT_ENV);
      environment.putAll(overriddenEnv);
      new SonarMcpServer(new StdioServerTransportProvider(new ObjectMapper(), clientToServerInputStream, serverToClientOutputStream), environment).start();
      client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
        .loggingConsumer(SonarMcpServerTestHarness::printLogs).build();
      client.initialize();
      client.setLoggingLevel(McpSchema.LoggingLevel.CRITICAL);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.clients.add(client);
    return client;
  }

  private static void printLogs(McpSchema.LoggingMessageNotification notification) {
    // do nothing by default to avoid too verbose tests
  }
}
