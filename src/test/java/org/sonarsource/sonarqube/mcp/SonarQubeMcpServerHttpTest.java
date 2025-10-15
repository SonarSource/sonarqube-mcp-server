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
package org.sonarsource.sonarqube.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SonarQubeMcpServerHttpTest {

  @Test
  void should_use_stdio_transport_by_default() {
    var environment = createTestEnvironment();
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  @Test
  void should_use_http_transport_when_enabled() {
    var environment = createTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "8080");
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(8080);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("127.0.0.1");
  }

  @Test
  void should_use_custom_http_configuration() {
    var environment = createTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    environment.put("SONARQUBE_HTTP_PORT", "9000");
    environment.put("SONARQUBE_HTTP_HOST", "0.0.0.0");
    
    var server = new SonarQubeMcpServer(environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(9000);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("0.0.0.0");
  }

  @Test
  void should_have_same_tools_regardless_of_transport() {
    var environment = createTestEnvironment();

    var stdioServer = new SonarQubeMcpServer(environment);
    var stdioTools = stdioServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();
    
    environment.put("SONARQUBE_HTTP_ENABLED", "true");
    var httpServer = new SonarQubeMcpServer(environment);
    var httpTools = httpServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();
    
    assertThat(httpTools)
      .isEqualTo(stdioTools)
      .isNotEmpty();
  }

  @Test
  void should_not_create_http_manager_when_disabled() {
    var environment = createTestEnvironment();
    environment.put("SONARQUBE_HTTP_ENABLED", "false");
    
    var server = new SonarQubeMcpServer(environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  private Map<String, String> createTestEnvironment() {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", "https://sonarcloud.io");
    environment.put("SONARQUBE_ORG", "test-org");
    return environment;
  }
}
