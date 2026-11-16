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
import java.util.List;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

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
  VORTEX_PROXY;

  private static final McpLogger LOG = McpLogger.getInstance();

  public boolean isAvailable(StartupFacts facts, AnalysisToolSelection.Mode analysisMode) {
    return switch (this) {
      case COMMON -> true;
      case CLOUD_ONLY -> facts.cloud();
      case SERVER_ONLY -> !facts.cloud();
      case ARCHITECTURE -> facts.architecture();
      case DEPENDENCY_RISKS -> facts.dependencyRisks();
      case AGENTIC_READINESS -> facts.sara();
      case LOCAL_ANALYSIS -> analysisMode == AnalysisToolSelection.Mode.LOCAL;
      case IDE_ANALYSIS -> analysisMode == AnalysisToolSelection.Mode.IDE;
      case VORTEX_ANALYSIS -> analysisMode == AnalysisToolSelection.Mode.VORTEX;
      case VORTEX_PROXY -> facts.vortexProxy();
    };
  }

  /** Resolves startup-only probes once; caller-specific authorization stays in the tools and backend. */
  public static StartupFacts resolve(McpServerLaunchConfiguration configuration, ServerApi api, @Nullable Version serverVersion,
    @Nullable ResolvedOrganization organization, List<ResolvedOrganization> organizations) {
    var cloud = configuration.isSonarQubeCloud();
    var http = configuration.isHttpEnabled();
    var architecture = cloud || (serverVersion != null && serverVersion.satisfiesMinRequirement(Version.create("2026.6")));
    var dependencyRisks = selected(configuration, ToolDescriptor.SEARCH_DEPENDENCY_RISKS)
      && (http || cloud || api.featuresApi().listFeatures().contains(Feature.SCA));
    var vortexSelected = selected(configuration, ToolDescriptor.VORTEX_PROXY);
    var vortexProxy = !http && vortexSelected && api.orgFeatureEntitlements().isVortexEnabledForOrg(organization);
    var vortexAnalysis = !cloud && vortexProxy;
    if (cloud && !http && !configuration.isForceLocalAnalysis() && configuration.getWorkspacePath() != null && vortexSelected) {
      vortexAnalysis = organization != null ? vortexProxy : api.orgFeatureEntitlements().isVortexEnabledForAnyOrganization(organizations);
    }
    var sara = saraEnabled(configuration, api, organization, organizations);
    return new StartupFacts(http ? TransportMode.HTTP : TransportMode.STDIO, cloud, architecture, dependencyRisks, sara, vortexProxy, vortexAnalysis);
  }

  private static boolean saraEnabled(McpServerLaunchConfiguration configuration, ServerApi api,
    @Nullable ResolvedOrganization organization, List<ResolvedOrganization> organizations) {
    if (!selected(configuration, ToolDescriptor.START_AGENTIC_READINESS_ASSESSMENT) || !configuration.isSonarQubeCloud()) {
      return false;
    }
    // HTTP has no organization at startup. The SARA backend enforces entitlement with the invocation credentials.
    if (configuration.isHttpEnabled()) {
      return true;
    }
    for (var candidate : organization == null ? organizations : List.of(organization)) {
      try {
        if (api.orgFeatureEntitlements().isSaraEnabledForOrg(candidate)) {
          return true;
        }
      } catch (Exception e) {
        LOG.warn("Could not check agentic readiness feature flag for organization '" + candidate.key() + "': " + e.getMessage());
      }
    }
    return false;
  }

  private static boolean selected(McpServerLaunchConfiguration configuration, ToolDescriptor descriptor) {
    return Tool.matchesCategories(descriptor.getCategories(), configuration.getEnabledToolsets());
  }

  public record StartupFacts(TransportMode transport, boolean cloud, boolean architecture, boolean dependencyRisks,
    boolean sara, boolean vortexProxy, boolean vortexAnalysis) {
  }
}
