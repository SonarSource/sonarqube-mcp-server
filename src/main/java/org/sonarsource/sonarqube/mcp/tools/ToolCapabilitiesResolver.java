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
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode;

/** Resolves startup capabilities once, reusing the existing API clients and entitlement cache. */
public final class ToolCapabilitiesResolver {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final McpServerLaunchConfiguration configuration;
  private final ServerApi serverApi;
  private final HttpClientProvider httpClientProvider;
  @Nullable
  private final Version serverVersion;

  public ToolCapabilitiesResolver(McpServerLaunchConfiguration configuration, ServerApi serverApi, HttpClientProvider httpClientProvider,
    @Nullable Version serverVersion) {
    this.configuration = configuration;
    this.serverApi = serverApi;
    this.httpClientProvider = httpClientProvider;
    this.serverVersion = serverVersion;
  }

  public Resolution resolve(@Nullable ResolvedOrganization organization, List<ResolvedOrganization> organizations) {
    var cloud = configuration.isSonarQubeCloud();
    // Community Build uses short-year versions and does not include Architecture.
    var architecture = cloud || (serverVersion != null && serverVersion.satisfiesMinRequirement(Version.create("2026.6")));
    var dependencyRisks = configuration.isToolCategoryEnabled(ToolCategory.DEPENDENCY_RISKS)
      && (configuration.isHttpEnabled() || cloud || serverApi.featuresApi().listFeatures().contains(Feature.SCA));
    var vortexRelevant = configuration.isToolCategoryEnabled(ToolCategory.CAG)
      || configuration.isToolCategoryEnabled(ToolCategory.VORTEX)
      || configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS);
    var vortexProxy = !configuration.isHttpEnabled() && vortexRelevant && serverApi.orgFeatureEntitlements().isVortexEnabledForOrg(organization);
    var vortexAnalysis = resolveVortexAnalysis(organization, organizations, vortexRelevant, vortexProxy);
    var agenticReadiness = resolveAgenticReadiness(organization, organizations);
    var analysis = resolveAnalysis(vortexAnalysis);
    return new Resolution(new ToolCapabilities(cloud, architecture, dependencyRisks, agenticReadiness, vortexProxy, analysis.mode()), analysis.bridgeClient());
  }

  private boolean resolveVortexAnalysis(@Nullable ResolvedOrganization organization, List<ResolvedOrganization> organizations,
    boolean vortexRelevant, boolean vortexProxy) {
    if (!configuration.isSonarQubeCloud()) {
      return vortexProxy;
    }
    if (configuration.isHttpEnabled() || configuration.isForceLocalAnalysis() || configuration.getWorkspacePath() == null || !vortexRelevant) {
      return false;
    }
    return organization != null ? vortexProxy : serverApi.orgFeatureEntitlements().isVortexEnabledForAnyOrganization(organizations);
  }

  private AnalysisSelection resolveAnalysis(boolean vortexAnalysis) {
    if (configuration.isHttpEnabled() && configuration.getSonarQubeToken() == null) {
      LOG.debug("HTTP mode without a startup token - skipping analysis tool registration");
      return new AnalysisSelection(AnalysisMode.NONE, null);
    }
    if (vortexAnalysis) {
      if (configuration.getWorkspacePath() != null) {
        return new AnalysisSelection(AnalysisMode.VORTEX, null);
      }
      LOG.info("No workspace path configured; advanced analysis tool is not registered");
      return new AnalysisSelection(AnalysisMode.NONE, null);
    }
    if (configuration.isSonarQubeCloud() && configuration.isForceLocalAnalysis()) {
      return new AnalysisSelection(AnalysisMode.LOCAL, null);
    }
    if (!configuration.isHttpEnabled() && configuration.getSonarQubeIdePort() != null) {
      var bridgeClient = createBridgeClient();
      if (bridgeClient.isAvailable()) {
        LOG.info("SonarQube for IDE integration detected");
        return new AnalysisSelection(AnalysisMode.IDE, bridgeClient);
      }
    }
    LOG.info("Standard analysis mode (no IDE bridge)");
    return new AnalysisSelection(AnalysisMode.LOCAL, null);
  }

  private boolean resolveAgenticReadiness(@Nullable ResolvedOrganization organization, List<ResolvedOrganization> organizations) {
    if (!configuration.isToolCategoryEnabled(ToolCategory.AGENTIC_READINESS)) {
      LOG.debug("Agentic readiness toolset is not enabled, skipping tools initialization");
      return false;
    }
    if (!configuration.isSonarQubeCloud()) {
      LOG.debug("Agentic readiness is only available on SonarQube Cloud, skipping tools initialization");
      return false;
    }
    // HTTP toolset selection already enforces opt-in; entitlement is checked per call.
    if (configuration.isHttpEnabled()) {
      return true;
    }
    var relevantOrganizations = organization != null ? List.of(organization) : organizations;
    for (var candidate : relevantOrganizations) {
      try {
        if (serverApi.orgFeatureEntitlements().isSaraEnabledForOrg(candidate)) {
          return true;
        }
      } catch (Exception e) {
        LOG.warn("Could not check agentic readiness feature flag for organization '" + candidate.key() + "': " + e.getMessage());
      }
    }
    return false;
  }

  private SonarQubeIdeBridgeClient createBridgeClient() {
    LOG.info("Initializing SonarQube for IDE bridge client...");
    var bridgeUrl = "http://" + configuration.getHostMachineAddress() + ":" + configuration.getSonarQubeIdePort();
    LOG.info("Bridge URL: " + bridgeUrl);
    var bridgeHelper = new ServerApiHelper(new EndpointParams(bridgeUrl, null, null, false), httpClientProvider.getHttpClientForBridge());
    return new SonarQubeIdeBridgeClient(bridgeHelper);
  }

  public record Resolution(ToolCapabilities capabilities, @Nullable SonarQubeIdeBridgeClient bridgeClient) {
  }

  private record AnalysisSelection(AnalysisMode mode, @Nullable SonarQubeIdeBridgeClient bridgeClient) {
  }
}
