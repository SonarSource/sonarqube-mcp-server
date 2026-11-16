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
package org.sonarsource.sonarqube.mcp.tools;

import jakarta.annotation.Nullable;
import java.util.function.Supplier;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

/** Selects the analysis backend once, before the catalog constructs any tools. */
public record AnalysisToolSelection(Mode mode, @Nullable SonarQubeIdeBridgeClient ideBridge) {

  private static final McpLogger LOG = McpLogger.getInstance();

  public enum Mode {
    NONE, LOCAL, IDE, VORTEX
  }

  public static AnalysisToolSelection resolve(McpServerLaunchConfiguration configuration, ToolAvailability.StartupFacts availability,
    BackendService backend, Supplier<SonarQubeIdeBridgeClient> bridgeFactory) {
    if (configuration.isHttpEnabled() && configuration.getSonarQubeToken() == null) {
      LOG.debug("HTTP mode without a startup token - skipping analysis tool registration");
      return new AnalysisToolSelection(Mode.NONE, null);
    }
    if (availability.vortexAnalysis()) {
      // On Server, an entitled Vortex installation suppresses local/IDE fallback even without a workspace.
      if (configuration.getWorkspacePath() == null) {
        LOG.info("No workspace path configured; advanced analysis tool is not registered");
        return new AnalysisToolSelection(Mode.NONE, null);
      }
      return new AnalysisToolSelection(Mode.VORTEX, null);
    }
    if (configuration.isSonarQubeCloud() && configuration.isForceLocalAnalysis()) {
      return new AnalysisToolSelection(Mode.LOCAL, null);
    }
    if (!configuration.isHttpEnabled() && configuration.getSonarQubeIdePort() != null) {
      var bridge = bridgeFactory.get();
      if (bridge.isAvailable()) {
        LOG.info("SonarQube for IDE integration detected");
        backend.notifySonarQubeIdeIntegration();
        return new AnalysisToolSelection(Mode.IDE, bridge);
      }
    }
    LOG.info("Standard analysis mode (no IDE bridge)");
    return new AnalysisToolSelection(Mode.LOCAL, null);
  }
}
