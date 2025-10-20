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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.harness.MockWebServer;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

class SonarQubeMcpServerIdeBridgeTest {

  private MockWebServer mockIdeEmbeddedServer;

  @BeforeEach
  void prepare() {
    mockIdeEmbeddedServer = new MockWebServer(64130);
    mockIdeEmbeddedServer.start();
  }

  @AfterEach
  void cleanup() {
    mockIdeEmbeddedServer.stop();
  }

  @SonarQubeMcpServerTest
  void should_add_bridge_related_tools_when_ide_bridge_is_available(SonarQubeMcpServerTestHarness testHarness) {
    mockIdeEmbeddedServer.stubFor(get(SonarQubeIdeBridgeClient.STATUS_PATH)
      .willReturn(aResponse().withStatus(200)));
    testHarness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
      .willReturn(aResponse().withStatus(200).withBody("""
        {
          "id": "20150504120436",
          "version": "2025.4",
          "status": "UP"
        }""")));
    testHarness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.FEATURE_ENABLED_PATH).willReturn(okJson("""
        {
          "enabled": true
        }
        """)));

    var environment = new HashMap<String, String>();
    environment.put("SONARQUBE_URL", testHarness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_IDE_PORT", Integer.toString(mockIdeEmbeddedServer.getPort()));
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir") + "/test-sonar-storage");

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(new ObjectMapper()), environment);

    var supportedTools = server.getSupportedTools();
    assertThat(supportedTools)
      .noneMatch(AnalysisTool.class::isInstance)
      .anyMatch(AnalyzeFileListTool.class::isInstance)
      .anyMatch(ToggleAutomaticAnalysisTool.class::isInstance);
  }

  @SonarQubeMcpServerTest
  void should_add_analysis_tool_when_ide_bridge_is_not_available(SonarQubeMcpServerTestHarness testHarness) {
    testHarness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
      .willReturn(aResponse().withStatus(200).withBody("""
        {
          "id": "20150504120436",
          "version": "2025.4",
          "status": "UP"
        }""")));
    testHarness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.FEATURE_ENABLED_PATH).willReturn(okJson("""
        {
          "enabled": true
        }
        """)));

    var environment = new HashMap<String, String>();
    environment.put("SONARQUBE_URL", testHarness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir") + "/test-sonar-storage");

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(new ObjectMapper()), environment);

    var supportedTools = server.getSupportedTools();
    assertThat(supportedTools)
      .anyMatch(AnalysisTool.class::isInstance)
      .noneMatch(AnalyzeFileListTool.class::isInstance)
      .noneMatch(ToggleAutomaticAnalysisTool.class::isInstance);
  }

}
