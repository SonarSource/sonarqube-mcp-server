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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ToggleAutomaticAnalysisToolTests {

  private SonarQubeIdeBridgeClient bridgeClient;
  private ToggleAutomaticAnalysisTool underTest;

  @BeforeEach
  void setUp() {
    bridgeClient = mock(SonarQubeIdeBridgeClient.class);
    underTest = new ToggleAutomaticAnalysisTool(bridgeClient);
  }

  @Test
  void it_should_validate_output_schema_and_annotations() {
    assertThat(underTest.definition().annotations()).isNotNull();
    assertThat(underTest.definition().annotations().readOnlyHint()).isFalse();
    assertThat(underTest.definition().annotations().openWorldHint()).isTrue();
    assertThat(underTest.definition().annotations().idempotentHint()).isFalse();
    assertThat(underTest.definition().annotations().destructiveHint()).isFalse();

    assertSchemaEquals(underTest.definition().outputSchema(), """
      {
          "type":"object",
          "properties":{
             "enabled":{
                "type":"boolean",
                "description":"The new automatic analysis state"
             },
             "message":{
                "type":"string",
                "description":"Success or error message"
             },
             "success":{
                "type":"boolean",
                "description":"Whether the operation was successful"
             }
          },
          "required":[
             "enabled",
             "message",
             "success"
          ]
       }
      """);
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
        ToggleAutomaticAnalysisTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.", true));
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
      var successResponse = new SonarQubeIdeBridgeClient.ToggleAutomaticAnalysisResponse(true, "Automatic analysis has been enabled.");
      when(bridgeClient.requestToggleAutomaticAnalysis(true)).thenReturn(successResponse);

      var result = underTest.execute(new Tool.Arguments(Map.of(
        ToggleAutomaticAnalysisTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertResultEquals(result, """
        {
          "success" : true,
          "enabled" : true,
          "message" : "Successfully toggled automatic analysis to true."
        }""");
    }

    @Test
    void it_should_successfully_disable_automatic_analysis() {
      var successResponse = new SonarQubeIdeBridgeClient.ToggleAutomaticAnalysisResponse(true, "Automatic analysis has been disabled.");
      when(bridgeClient.requestToggleAutomaticAnalysis(false)).thenReturn(successResponse);

      var result = underTest.execute(new Tool.Arguments(Map.of(
        ToggleAutomaticAnalysisTool.ENABLED_PROPERTY, false
      ))).toCallToolResult();

      assertResultEquals(result, """
        {
          "success" : true,
          "enabled" : false,
          "message" : "Successfully toggled automatic analysis to false."
        }""");
    }

    @Test
    void it_should_handle_unsuccessful_response() {
      var failureResponse = new SonarQubeIdeBridgeClient.ToggleAutomaticAnalysisResponse(false, "Failed to enable automatic analysis.");
      when(bridgeClient.requestToggleAutomaticAnalysis(true)).thenReturn(failureResponse);

      var result = underTest.execute(new Tool.Arguments(Map.of(
        ToggleAutomaticAnalysisTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to enable automatic analysis.", true));
    }

    @Test
    void it_should_return_an_error_if_the_bridge_request_fails() {
      when(bridgeClient.requestToggleAutomaticAnalysis(true)).thenReturn(new SonarQubeIdeBridgeClient.ToggleAutomaticAnalysisResponse(false, null));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        ToggleAutomaticAnalysisTool.ENABLED_PROPERTY, true
      ))).toCallToolResult();

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Failed to toggle automatic analysis. Check logs for details.", true));
    }
  }

}
