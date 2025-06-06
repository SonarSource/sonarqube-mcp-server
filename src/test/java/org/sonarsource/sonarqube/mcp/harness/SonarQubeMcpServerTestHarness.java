/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;

public class SonarQubeMcpServerTestHarness extends TypeBasedParameterResolver<SonarQubeMcpServerTestHarness> implements AfterEachCallback {
  private static final Map<String, String> DEFAULT_ENV_TEMPLATE = Map.of(
    "PLUGINS_PATH", "build/sonarqube-mcp-server/plugins",
    "SONARQUBE_URL", "fake.url");
  private final List<McpSyncClient> clients = new ArrayList<>();
  private Path tempStoragePath;

  @Override
  public SonarQubeMcpServerTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
  }

  @Override
  public void afterEach(ExtensionContext context) {
    clients.forEach(McpSyncClient::closeGracefully);
    clients.clear();
    cleanupTempStoragePath();
  }

  private void cleanupTempStoragePath() {
    if (tempStoragePath != null && Files.exists(tempStoragePath)) {
      try {
        Files.walk(tempStoragePath)
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              // Ignore cleanup errors
            }
          });
      } catch (IOException e) {
        // Ignore cleanup errors
      }
      tempStoragePath = null;
    }
  }

  public McpSyncClient newClient() {
    return newClient(Map.of());
  }

  public McpSyncClient newClient(Map<String, String> overriddenEnv) {
    if (tempStoragePath == null) {
      try {
        tempStoragePath = Files.createTempDirectory("sonarqube-mcp-test-storage-");
      } catch (IOException e) {
        throw new RuntimeException("Failed to create temporary storage directory", e);
      }
    }
    
    McpSyncClient client;
    try {
      var clientToServerOutputStream = new PipedOutputStream();
      var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
      var serverToClientOutputStream = new PipedOutputStream();
      var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
      var environment = new HashMap<>(DEFAULT_ENV_TEMPLATE);
      environment.put("STORAGE_PATH", tempStoragePath.toString());
      environment.putAll(overriddenEnv);
      new SonarQubeMcpServer(new StdioServerTransportProvider(new ObjectMapper(), clientToServerInputStream, serverToClientOutputStream), environment).start();
      client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
        .loggingConsumer(SonarQubeMcpServerTestHarness::printLogs).build();
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
