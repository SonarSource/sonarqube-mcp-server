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

import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ToggleAutomaticAnalysisTool extends Tool {

  public static final String TOOL_NAME = "toggle_automatic_analysis";
  public static final String ENABLED_PROPERTY = "enabled";

  private final SonarQubeIdeBridgeClient bridgeClient;

  public ToggleAutomaticAnalysisTool(SonarQubeIdeBridgeClient bridgeClient) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Enable or disable SonarQube for IDE automatic analysis. " +
        "When enabled, SonarQube for IDE will automatically analyze files as they are modified in the working directory. " +
        "When disabled, automatic analysis is turned off.")
      .addBooleanProperty(ENABLED_PROPERTY, "Enable or disable the automatic analysis")
      .build());
    this.bridgeClient = bridgeClient;
  }

  @Override
  public Result execute(Arguments arguments) {
    if (!bridgeClient.isAvailable()) {
      return Result.failure("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.");
    }

    var enabled = arguments.getBooleanOrThrow(ENABLED_PROPERTY);

    var response = bridgeClient.requestToggleAutomaticAnalysis(enabled);
    if (!response.isSuccessful()) {
      var errorMessage = response.errorMessage();
      if (errorMessage == null) {
        errorMessage = "Failed to toggle automatic analysis. Check logs for details.";
      }
      return Result.failure(errorMessage);
    }

    return Result.success("Successfully toggled automatic analysis to " + enabled + ".");
  }

}
