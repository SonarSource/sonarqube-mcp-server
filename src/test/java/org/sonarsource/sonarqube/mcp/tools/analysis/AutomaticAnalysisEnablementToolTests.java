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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutomaticAnalysisEnablementToolTests {

  private SonarQubeIdeBridgeClient bridgeClient;
  private AutomaticAnalysisEnablementTool underTest;

  @BeforeEach
  void setUp() {
    bridgeClient = mock(SonarQubeIdeBridgeClient.class);
    underTest = new AutomaticAnalysisEnablementTool(bridgeClient);
  }

  @Nested
  class MissingPrerequisite {

    @Test
    void it_should_throw_exception_if_the_enabled_parameter_is_missing() {
      when(bridgeClient.isAvailable()).thenReturn(true);

      assertThat(assertThrows(org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException.class,
        () -> underTest.execute(new Tool.Arguments(Map.of()))))
        .hasMessage("Missing required argument: enabled");
    }
  }

  @Nested
  class WhenBridgeIsNotAvailable {
    @Test
    void it_should_return_an_error_if_sonarqube_for_ide_is_not_available() {
      when(bridgeClient.isAvailable()).thenReturn(false);

      var result = underTest.execute(new Tool.Arguments(Map.of(
        AutomaticAnalysisEnablementTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.", true));
    }
  }

  @Nested
  class WhenBridgeIsAvailable {
    @BeforeEach
    void setUp() {
      when(bridgeClient.isAvailable()).thenReturn(true);
    }

    @Test
    void it_should_successfully_enable_automatic_analysis() {
      var successResponse = new SonarQubeIdeBridgeClient.AutomaticAnalysisEnablementResponse(true, "Automatic analysis has been enabled.");
      when(bridgeClient.requestAutomaticAnalysisEnablement(true)).thenReturn(Optional.of(successResponse));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        AutomaticAnalysisEnablementTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Automatic analysis has been enabled.", false));
    }

    @Test
    void it_should_successfully_disable_automatic_analysis() {
      var successResponse = new SonarQubeIdeBridgeClient.AutomaticAnalysisEnablementResponse(true, "Automatic analysis has been disabled.");
      when(bridgeClient.requestAutomaticAnalysisEnablement(false)).thenReturn(Optional.of(successResponse));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        AutomaticAnalysisEnablementTool.ENABLED_PROPERTY, false
      ))).toCallToolResult();

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Automatic analysis has been disabled.", false));
    }

    @Test
    void it_should_handle_unsuccessful_response() {
      var failureResponse = new SonarQubeIdeBridgeClient.AutomaticAnalysisEnablementResponse(false, "Failed to enable automatic analysis.");
      when(bridgeClient.requestAutomaticAnalysisEnablement(true)).thenReturn(Optional.of(failureResponse));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        AutomaticAnalysisEnablementTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to enable automatic analysis.", false));
    }

    @Test
    void it_should_return_an_error_if_the_bridge_request_fails() {
      when(bridgeClient.requestAutomaticAnalysisEnablement(true)).thenReturn(Optional.empty());

      var result = underTest.execute(new Tool.Arguments(Map.of(
        AutomaticAnalysisEnablementTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to update automatic analysis enablement. Check logs for details.", true));
    }
  }

}
