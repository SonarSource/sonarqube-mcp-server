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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;

import static org.assertj.core.api.Assertions.assertThat;

class VortexOnServerTests {

  private static final String LICENCE_LOG = "Vortex is not licensed on this SonarQube Server. Ask your administrator.";
  private static final String VORTEX_ENABLED_LOG = "Vortex context is enabled";

  private Logger mcpLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void captureLogs() {
    mcpLogger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    mcpLogger.addAppender(logAppender);
    mcpLogger.setLevel(Level.INFO);
  }

  @AfterEach
  void stopCapturingLogs() {
    if (mcpLogger != null && logAppender != null) {
      mcpLogger.detachAppender(logAppender);
      logAppender.stop();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_server_when_both_hubs_are_entitled(SonarQubeMcpServerTestHarness harness) {
    harness.stubServerCagEntitlement(true);
    harness.stubServerA3sEntitlement(true);
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverCagPath())).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverA3sPath())).isTrue();
    assertThat(logMessages()).contains(VORTEX_ENABLED_LOG).doesNotContain(LICENCE_LOG);
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_server_without_the_cag_hub(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverCagPath())).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverA3sPath())).isFalse();
    assertThat(logMessages()).doesNotContain(VORTEX_ENABLED_LOG, LICENCE_LOG);
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_an_unlicensed_server(SonarQubeMcpServerTestHarness harness) {
    harness.stubServerCagEntitlement(false);
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverCagPath())).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(serverA3sPath())).isFalse();
    assertThat(logMessages()).contains(LICENCE_LOG).doesNotContain(VORTEX_ENABLED_LOG);
  }

  private List<String> logMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private static String serverCagPath() {
    return "/api/v2" + CagApi.CAG_ENTITLEMENT_PATH + CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER;
  }

  private static String serverA3sPath() {
    return "/api/v2" + A3sAnalysisApi.A3S_ORG_ENTITLEMENT_PATH + CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER;
  }
}
