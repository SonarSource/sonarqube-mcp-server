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
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class SonarQubeMcpServerTestHarness extends TypeBasedParameterResolver<SonarQubeMcpServerTestHarness> implements AfterEachCallback, BeforeEachCallback, AfterAllCallback {
  private static final Map<String, String> DEFAULT_ENV_TEMPLATE = Map.of(
    "SONARQUBE_TOKEN", "token");
  private final List<McpSyncClient> clients = new ArrayList<>();
  private Path tempStoragePath;
  private final MockWebServer mockSonarQubeServer = new MockWebServer();
  
  // Shared plugin directory across all tests in a class to avoid expensive copying
  private static Path sharedPluginDirectory;
  private static final Object PLUGIN_DIR_LOCK = new Object();
  private boolean pluginsEnabled = false;

  @Override
  public SonarQubeMcpServerTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
  }

  @Override
  public void afterAll(ExtensionContext context) {
    // Clean up shared plugin directory after all tests in the class
    synchronized (PLUGIN_DIR_LOCK) {
      if (sharedPluginDirectory != null && Files.exists(sharedPluginDirectory)) {
        try {
          FileUtils.deleteDirectory(sharedPluginDirectory.toFile());
        } catch (IOException e) {
          // Ignore cleanup errors
        }
        sharedPluginDirectory = null;
      }
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    mockSonarQubeServer.start();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    clients.forEach(McpSyncClient::closeGracefully);
    clients.clear();
    cleanupTempStoragePath();
    mockSonarQubeServer.stop();
    pluginsEnabled = false;
  }

  private void cleanupTempStoragePath() {
    // Don't delete the shared plugin directory - it's cleaned up in afterAll
    if (tempStoragePath != null && !tempStoragePath.equals(sharedPluginDirectory) && Files.exists(tempStoragePath)) {
      try {
        Files.delete(tempStoragePath);
      } catch (IOException e) {
        // Ignore cleanup errors
      }
    }
    tempStoragePath = null;
  }

  public MockWebServer getMockSonarQubeServer() {
    return mockSonarQubeServer;
  }

  public SonarQubeMcpServerTestHarness withPlugins() {
    this.pluginsEnabled = true;
    ensureSharedPluginDirectoryExists();
    return this;
  }

  private void ensureSharedPluginDirectoryExists() {
    synchronized (PLUGIN_DIR_LOCK) {
      if (sharedPluginDirectory == null) {
        try {
          sharedPluginDirectory = Files.createTempDirectory("sonarqube-mcp-test-plugins-shared");
          FileUtils.copyDirectoryToDirectory(Paths.get("build/sonarqube-mcp-server/plugins").toFile(), sharedPluginDirectory.toFile());
        } catch (IOException e) {
          throw new RuntimeException("Failed to create shared plugin directory", e);
        }
      }
    }
  }

  public SonarQubeMcpTestClient newClient() {
    return newClient(Map.of());
  }

  public SonarQubeMcpTestClient newClient(Map<String, String> overriddenEnv) {
    if (overriddenEnv.containsKey("STORAGE_PATH")) {
      tempStoragePath = Paths.get(overriddenEnv.get("STORAGE_PATH"));
    } else {
      try {
        if (pluginsEnabled && sharedPluginDirectory != null) {
          tempStoragePath = sharedPluginDirectory;
        } else {
          // For tests without plugins, create a minimal temp directory
          tempStoragePath = Files.createTempDirectory("sonarqube-mcp-test-storage-" + UUID.randomUUID());
        }
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
    environment.putAll(overriddenEnv);
    // Only set SONARQUBE_URL if not already set
    if (!environment.containsKey("SONARQUBE_URL")) {
      environment.put("SONARQUBE_URL", mockSonarQubeServer.baseUrl());
    }
    prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(clientToServerInputStream, serverToClientOutputStream),
      null, environment);
    server.start();

    var client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
      .loggingConsumer(SonarQubeMcpServerTestHarness::printLogs).build();
    client.initialize();
    this.clients.add(client);
    
    // Wait for background initialization to complete before returning
    // This ensures tools are fully loaded and available for tests
    try {
      server.waitForInitialization();
    } catch (Exception e) {
      throw new RuntimeException("Server initialization failed", e);
    }
    
    // Wait for client transport to be ready to handle requests
    // Tool notifications are asynchronous and may still be propagating through the transport
    await().atMost(2, SECONDS)
      .pollInterval(10, TimeUnit.MILLISECONDS)
      .ignoreExceptions()
      .until(() -> {
        client.listTools();
        return true;
      });
    
    return new SonarQubeMcpTestClient(client);
  }

  public void prepareMockWebServer(Map<String, String> environment) {
    if (!environment.containsKey("SONARQUBE_ORG")) {
      var version = "2025.4";
      if (environment.containsKey("SONARQUBE_VERSION")) {
        version = environment.get("SONARQUBE_VERSION");
      }
      mockSonarQubeServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(String.format("""
            {
              "id": "20150504120436",
              "version": "%s",
              "status": "UP"
            }""", version).getBytes(StandardCharsets.UTF_8)))));
    }
    
    // Only set up plugin stubs if plugins are enabled for this test
    if (pluginsEnabled) {
      mockSonarQubeServer.stubFor(get(PluginsApi.INSTALLED_PLUGINS_PATH).willReturn(okJson("""
        {
            "plugins": [
              {
                "key": "php",
                "filename": "sonar-php-plugin-3.54.0.15452.jar",
                "sonarLintSupported": true
              }
            ]
          }
        """)));
      try {
        mockSonarQubeServer.stubFor(get(PluginsApi.DOWNLOAD_PLUGINS_PATH + "?plugin=php")
          .willReturn(aResponse().withBody(Files.readAllBytes(Paths.get("build/sonarqube-mcp-server/plugins/sonar-php-plugin-3.54.0.15452.jar")))));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      // Return empty plugin list when plugins are not enabled
      mockSonarQubeServer.stubFor(get(PluginsApi.INSTALLED_PLUGINS_PATH).willReturn(okJson("""
        {
            "plugins": []
          }
        """)));
    }

    // Configure SCA feature check based on server type
    if (environment.containsKey("SONARQUBE_ORG")) {
      if (!mockSonarQubeServer.isStubConfigured(ScaApi.FEATURE_ENABLED_PATH)) {
        var orgParameter = "?organization=" + environment.get("SONARQUBE_ORG");
        mockSonarQubeServer.stubFor(get(ScaApi.FEATURE_ENABLED_PATH + orgParameter).willReturn(okJson("""
          {
            "enabled": true
          }
          """)));
      }
    } else {
      if (!mockSonarQubeServer.isStubConfigured(FeaturesApi.FEATURES_LIST_PATH)) {
        mockSonarQubeServer.stubFor(get(FeaturesApi.FEATURES_LIST_PATH).willReturn(okJson("""
          ["sca"]
          """)));
      }
    }

  }

  private static void printLogs(McpSchema.LoggingMessageNotification notification) {
    // do nothing by default to avoid too verbose tests
  }
}
