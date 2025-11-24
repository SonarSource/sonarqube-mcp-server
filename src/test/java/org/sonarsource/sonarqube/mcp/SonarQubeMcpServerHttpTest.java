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
package org.sonarsource.sonarqube.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

class SonarQubeMcpServerHttpTest {

  @SonarQubeMcpServerTest
  void should_use_stdio_transport_by_default(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  @SonarQubeMcpServerTest
  void should_use_http_transport_when_enabled(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "8080");
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(8080);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("127.0.0.1");
  }

  @SonarQubeMcpServerTest
  void should_use_custom_http_configuration(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "9000");
    environment.put("SONARQUBE_HTTP_HOST", "0.0.0.0");
    // Authentication is now optional (will show warning)

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(9000);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("0.0.0.0");
  }

  @SonarQubeMcpServerTest
  void should_have_same_tools_regardless_of_transport(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());

    harness.prepareMockWebServer(environment);

    var stdioServer = new SonarQubeMcpServer(environment);
    stdioServer.start();
    var stdioTools = stdioServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();
    
    environment.put("SONARQUBE_TRANSPORT", "http");
    var httpServer = new SonarQubeMcpServer(environment);
    httpServer.start();
    var httpTools = httpServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();
    
    assertThat(httpTools)
      .isEqualTo(stdioTools)
      .isNotEmpty();
  }

  @SonarQubeMcpServerTest
  void should_not_create_http_manager_when_disabled(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "false");

    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(new ObjectMapper(), null), null, environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  private Map<String, String> createTestEnvironment(String baseUrl) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", baseUrl);
    return environment;
  }
}
