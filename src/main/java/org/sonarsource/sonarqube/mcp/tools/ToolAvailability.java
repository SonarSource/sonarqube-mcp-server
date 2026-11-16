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

/** Named requirements enforced by startup selection and the existing invocation guards. */
public enum ToolAvailability {
  COMMON,
  CLOUD_ONLY,
  SERVER_ONLY,
  /** Cloud, or supported Server version; backend permissions remain enforced on calls. */
  ARCHITECTURE,
  /** Server stdio requires SCA; SearchDependencyRisksTool checks the caller's version and entitlement. */
  DEPENDENCY_RISKS,
  /** Cloud only: stdio requires an enabled organization; HTTP requires opt-in and backend call enforcement. */
  AGENTIC_READINESS,
  /** Selected local analysis backend; HTTP requires a startup token. */
  LOCAL_ANALYSIS,
  /** Selected and available IDE bridge. */
  IDE_ANALYSIS,
  /** Selected Vortex analysis backend with a workspace; RunAdvancedCodeAnalysisTool checks the caller's organization. */
  VORTEX_ANALYSIS,
  /** Combined Vortex entitlement for the resolved organization. */
  VORTEX_PROXY
}
