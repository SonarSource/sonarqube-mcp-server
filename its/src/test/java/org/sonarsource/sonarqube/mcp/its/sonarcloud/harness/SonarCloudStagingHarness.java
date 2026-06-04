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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.harness;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.harness.BlockingQueueInputStream;
import org.sonarsource.sonarqube.mcp.harness.BlockingQueueOutputStream;
import org.sonarsource.sonarqube.mcp.harness.InMemoryClientTransport;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

/**
 * In-process MCP server harness wired to SonarQube Cloud staging (no WireMock).
 */
public class SonarCloudStagingHarness implements AutoCloseable {

  private static final String ALL_TOOLSETS = Arrays.stream(ToolCategory.values())
    .map(ToolCategory::getKey)
    .collect(Collectors.joining(","));

  private final List<McpSyncClient> clients = new ArrayList<>();
  private final List<SonarQubeMcpServer> servers = new ArrayList<>();
  private final List<Path> tempStoragePaths = new ArrayList<>();

  public SonarQubeMcpTestClient newStagingClient() {
    return newStagingClient(Map.of());
  }

  public SonarQubeMcpTestClient newStagingClient(Map<String, String> extraEnv) {
    final Path storagePath;
    try {
      storagePath = Files.createTempDirectory("sonarqube-mcp-sonarcloud-it-" + UUID.randomUUID());
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temporary storage directory", e);
    }
    tempStoragePaths.add(storagePath);

    var clientToServerBlockingQueue = new LinkedBlockingQueue<Integer>();
    var clientToServerOutputStream = new BlockingQueueOutputStream(clientToServerBlockingQueue);
    var clientToServerInputStream = new BlockingQueueInputStream(clientToServerBlockingQueue);
    var serverToClientBlockingQueue = new LinkedBlockingQueue<Integer>();
    var serverToClientOutputStream = new BlockingQueueOutputStream(serverToClientBlockingQueue);
    var serverToClientInputStream = new BlockingQueueInputStream(serverToClientBlockingQueue);

    var environment = new HashMap<String, String>();
    environment.put("SONARQUBE_TOKEN", SonarCloudStagingEnvironment.requireToken());
    environment.put("SONARQUBE_ORG", SonarCloudStagingEnvironment.SONARQUBE_ORG);
    environment.put("SONARQUBE_URL", SonarCloudStagingEnvironment.SONARQUBE_URL);
    environment.put("SONARQUBE_CLOUD_API_URL", SonarCloudStagingEnvironment.SONARQUBE_CLOUD_API_URL);
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_TOOLSETS", ALL_TOOLSETS);
    environment.put("TELEMETRY_DISABLED", "true");
    environment.put("STORAGE_PATH", storagePath.toString());
    environment.putAll(extraEnv);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(clientToServerInputStream, serverToClientOutputStream),
      null,
      environment);
    server.start();
    servers.add(server);

    var client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
      .loggingConsumer(SonarCloudStagingHarness::ignoreLogs)
      .build();
    client.initialize();
    clients.add(client);

    try {
      server.waitForInitialization();
    } catch (Exception e) {
      throw new RuntimeException("Server initialization failed against staging", e);
    }

    await().atMost(60, SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .ignoreExceptions()
      .until(() -> {
        client.listTools();
        return true;
      });

    return new SonarQubeMcpTestClient(client);
  }

  @Override
  public void close() {
    clients.forEach(McpSyncClient::closeGracefully);
    clients.clear();
    servers.forEach(SonarQubeMcpServer::shutdown);
    servers.clear();
    tempStoragePaths.forEach(SonarCloudStagingHarness::deleteDirectoryTree);
    tempStoragePaths.clear();
  }

  private static void deleteDirectoryTree(Path root) {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.delete(path);
        } catch (IOException ignored) {
          // best effort
        }
      });
    } catch (IOException ignored) {
      // ignore
    }
  }

  private static void ignoreLogs(McpSchema.LoggingMessageNotification notification) {
    // keep SonarQube Cloud integration tests quiet
  }
}
