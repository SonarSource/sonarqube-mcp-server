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

/** Startup decisions only; caller-specific authorization remains in the request and execution layers. */
public record ToolCapabilities(boolean cloud, boolean architecture, boolean dependencyRisks, boolean agenticReadiness,
                               boolean vortexProxy, AnalysisMode analysisMode) {

  public enum AnalysisMode {
    LOCAL, IDE, VORTEX, NONE
  }

  public boolean server() {
    return !cloud;
  }

  public boolean localAnalysis() {
    return analysisMode == AnalysisMode.LOCAL;
  }

  public boolean ideAnalysis() {
    return analysisMode == AnalysisMode.IDE;
  }

  public boolean vortexAnalysis() {
    return analysisMode == AnalysisMode.VORTEX;
  }
}
