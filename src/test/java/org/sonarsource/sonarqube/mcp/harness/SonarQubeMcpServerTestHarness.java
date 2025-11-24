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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
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
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
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
  private final MockWebServer mockSonarQubeServer = new MockWebServer();

  @Override
  public SonarQubeMcpServerTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
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

  public SonarQubeMcpTestClient newClient() {
    return newClient(Map.of());
  }

  public SonarQubeMcpTestClient newClient(Map<String, String> overriddenEnv) {
    if (overriddenEnv.containsKey("STORAGE_PATH")) {
      tempStoragePath = Paths.get(overriddenEnv.get("STORAGE_PATH"));
    } else {
      try {
        tempStoragePath = Files.createTempDirectory("sonarqube-mcp-test-storage-" + UUID.randomUUID());
        FileUtils.copyDirectoryToDirectory(Paths.get("build/sonarqube-mcp-server/plugins").toFile(), tempStoragePath.toFile());
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
    // Only set SONARQUBE_URL if not already set and not using SonarQube Cloud (no SONARQUBE_ORG)
    if (!environment.containsKey("SONARQUBE_URL") && !environment.containsKey("SONARQUBE_ORG")) {
      environment.put("SONARQUBE_URL", mockSonarQubeServer.baseUrl());
    } else if (environment.containsKey("SONARQUBE_ORG") && !environment.containsKey("SONARQUBE_URL")) {
      // For SonarQube Cloud tests, set the URL to the cloud URL (which will default to SonarQube Cloud URL in config)
      environment.put("SONARQUBE_CLOUD_URL", mockSonarQubeServer.baseUrl());
    }
    prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()), clientToServerInputStream, serverToClientOutputStream),
      null, environment);
    server.start();

    var client = McpClient.sync(new InMemoryClientTransport(serverToClientInputStream, clientToServerOutputStream))
      .loggingConsumer(SonarQubeMcpServerTestHarness::printLogs).build();
    client.initialize();
    this.clients.add(client);
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
    mockSonarQubeServer.stubFor(get(PluginsApi.INSTALLED_PLUGINS_PATH).willReturn(okJson("""
      {
          "plugins": [
            {
              "key": "php",
              "filename": "sonar-php-plugin-3.52.0.15197.jar",
              "sonarLintSupported": true
            }
          ]
        }
      """)));
    try {
      mockSonarQubeServer.stubFor(get(PluginsApi.DOWNLOAD_PLUGINS_PATH + "?plugin=php")
        .willReturn(aResponse().withBody(Files.readAllBytes(Paths.get("build/sonarqube-mcp-server/plugins/sonar-php-plugin-3.52.0.15197.jar")))));
    } catch (IOException e) {
      throw new RuntimeException(e);
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
