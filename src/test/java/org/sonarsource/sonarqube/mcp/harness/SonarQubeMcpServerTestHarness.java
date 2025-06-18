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
import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;

public class SonarQubeMcpServerTestHarness extends TypeBasedParameterResolver<SonarQubeMcpServerTestHarness> implements AfterEachCallback, BeforeEachCallback {
  private static final Map<String, String> DEFAULT_ENV_TEMPLATE = Map.of(
    "SONARQUBE_TOKEN", "token");
  private final List<McpSyncClient> clients = new ArrayList<>();
  private Path tempStoragePath;
  private final List<SonarQubeMcpServer> servers = new ArrayList<>();
  private final MockWebServer mockSonarQubeServer = new MockWebServer();

  @Override
  public SonarQubeMcpServerTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    mockSonarQubeServer.start();
    mockSonarQubeServer.stubFor(get(PluginsApi.INSTALLED_PLUGINS_PATH).willReturn(okJson("""
      {
          "plugins": [
            {
              "key": "php",
              "filename": "sonar-php-plugin-3.45.0.12991.jar",
              "sonarLintSupported": true
            }
          ]
        }
      """)));
    mockSonarQubeServer.stubFor(get(PluginsApi.DOWNLOAD_PLUGINS_PATH + "?plugin=php")
      .willReturn(aResponse().withBody(Files.readAllBytes(Paths.get("build/sonarqube-mcp-server/plugins/sonar-php-plugin-3.45.0.12991.jar")))));
  }

  @Override
  public void afterEach(ExtensionContext context) {
    clients.forEach(McpSyncClient::closeGracefully);
    clients.clear();
    servers.forEach(server -> {
      try {
        server.shutdown();
      } catch (Exception e) {
        // Log error but continue cleanup
        System.err.println("Error shutting down server: " + e.getMessage());
      }
    });
    servers.clear();
    cleanupTempStoragePath();
    mockSonarQubeServer.stop();
  }

  private void cleanupTempStoragePath() {
    if (tempStoragePath != null && Files.exists(tempStoragePath)) {
      try {
        Files.delete(tempStoragePath);
      } catch (IOException e) {
        // Ignore cleanup errors
      }
      tempStoragePath = null;
    }
  }

  public MockWebServer getMockSonarQubeServer() {
    return mockSonarQubeServer;
  }

  public McpSyncClient newClient() {
    return newClient(Map.of());
  }

  public McpSyncClient newClient(Map<String, String> overriddenEnv) {
    if (!overriddenEnv.containsKey("SONARQUBE_ORG")) {
      mockSonarQubeServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
      {
        "id": "20150504120436",
        "version": "2025.1",
        "status": "UP"
      }""".getBytes(StandardCharsets.UTF_8)))));
    }
    if (overriddenEnv.containsKey("STORAGE_PATH")) {
      tempStoragePath = Paths.get(overriddenEnv.get("STORAGE_PATH"));
    } else {
      try {
        tempStoragePath = Files.createTempDirectory("sonarqube-mcp-test-storage-" + UUID.randomUUID());
        FileUtils.copyDirectory(Paths.get("build/sonarqube-mcp-server/plugins").toFile(), tempStoragePath.toFile());
      } catch (IOException e) {
        throw new RuntimeException("Failed to create temporary storage directory", e);
      }
    }

    var clientToServerBlockingQueue = new LinkedBlockingQueue<Integer>();
    var clientToServerOutputStream = new BlockingQueueOutputStream(clientToServerBlockingQueue);
    var clientToServerInputStream = new BlockingQueueInputStream(clientToServerBlockingQueue);
    var serverToClientBlockingQueue = new LinkedBlockingQueue<Integer>();
    var serverToClientOutputStream = new BlockingQueueOutputStream(serverToClientBlockingQueue);
    var serverToClientInputStream = new BlockingQueueInputStream(serverToClientBlockingQueue);
    var environment = new HashMap<>(DEFAULT_ENV_TEMPLATE);
    environment.put("STORAGE_PATH", tempStoragePath.toString());
    environment.put("SONARQUBE_URL", mockSonarQubeServer.baseUrl());
    environment.putAll(overriddenEnv);

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(new ObjectMapper(), clientToServerInputStream, serverToClientOutputStream),
      environment);
    server.start();

    var client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
      .loggingConsumer(SonarQubeMcpServerTestHarness::printLogs).build();
    client.initialize();
    this.clients.add(client);
    this.servers.add(server);
    return client;
  }

  private static void printLogs(McpSchema.LoggingMessageNotification notification) {
    // do nothing by default to avoid too verbose tests
  }
}
