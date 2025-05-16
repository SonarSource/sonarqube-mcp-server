/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
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
    "PLUGIN_PATH", "build/sonar-mcp-server/plugins",
    "SONARQUBE_CLOUD_URL", "fake.url"
  );
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
      new SonarMcpServer().start(new StdioServerTransportProvider(new ObjectMapper(), clientToServerInputStream, serverToClientOutputStream), environment);
      client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream)).build();
      client.initialize();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.clients.add(client);
    return client;
  }
}
