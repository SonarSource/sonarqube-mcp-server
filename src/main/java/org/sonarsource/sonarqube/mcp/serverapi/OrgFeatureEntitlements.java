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
package org.sonarsource.sonarqube.mcp.serverapi;

import java.util.function.BiPredicate;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;

/**
 * Checks org-scoped feature entitlements gating optional toolsets (Vortex, agentic readiness),
 * sharing the org UUID resolution the underlying checks all require.
 */
public class OrgFeatureEntitlements {

  private static final McpLogger LOG = McpLogger.getInstance();

  @Nullable
  private final ServerApi api;

  public OrgFeatureEntitlements(@Nullable ServerApi api) {
    this.api = api;
  }

  /**
   * Vortex is one product: both hubs must be entitled. Cloud uses the org UUID against CAG
   * entitlement and A3S org-config. Server has no organizations, so both GETs use the nil
   * UUID placeholder; a 404 from either hub means Vortex is not on this instance.
   */
  public boolean isVortexEnabledForOrg(@Nullable ResolvedOrganization org) {
    if (api != null && !api.isSonarQubeCloud()) {
      var orgKey = org != null ? org.key() : "server";
      var placeholder = CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER;
      var cagEnabled = isCagEnabled(placeholder, orgKey);
      var a3sEnabled = isServerA3sEnabled(placeholder, orgKey);
      return cagEnabled && a3sEnabled;
    }
    return checkForOrg(org, (orgUuidV4, orgKey) -> isCagEnabled(orgUuidV4, orgKey) && isA3sEnabled(orgUuidV4, orgKey));
  }

  public boolean isSaraEnabledForOrg(@Nullable ResolvedOrganization org) {
    return checkForOrg(org, this::isSaraEnabled);
  }

  private boolean checkForOrg(@Nullable ResolvedOrganization org, BiPredicate<String, String> check) {
    if (api == null || org == null) {
      return false;
    }
    var orgKey = org.key();
    var orgUuidV4 = org.uuidV4() != null ? org.uuidV4() : api.organizationsApi().getOrganizationUuidV4(orgKey);
    if (orgUuidV4 == null) {
      LOG.debug("Entitlement check: could not resolve UUID for org '" + orgKey + "' - skipping");
      return false;
    }
    return check.test(orgUuidV4, orgKey);
  }

  private boolean isCagEnabled(String orgUuidV4, String orgKey) {
    var entitlement = api.cagApi().getCagEntitlement(orgUuidV4);
    if (entitlement == null) {
      LOG.debug("Vortex entitlement check: could not retrieve CAG entitlement for org '" + orgKey + "'");
      return false;
    }
    if (!entitlement.hasEntitlement()) {
      if (!api.isSonarQubeCloud()) {
        LOG.info("Vortex is not licensed on this SonarQube Server. Ask your administrator.");
      } else {
        LOG.debug("Vortex entitlement check: org '" + orgKey + "' is not entitled to use CAG");
      }
      return false;
    }
    return true;
  }

  private boolean isA3sEnabled(String orgUuidV4, String orgKey) {
    var config = api.a3sAnalysisApi().getA3sOrgConfig(orgUuidV4);
    if (config == null) {
      LOG.debug("Vortex entitlement check: could not retrieve A3S org config for org '" + orgKey + "'");
      return false;
    }
    if (!config.enabled()) {
      LOG.debug("Vortex entitlement check: advanced analysis is not enabled for org '" + orgKey + "'");
      return false;
    }
    return true;
  }

  private boolean isServerA3sEnabled(String orgUuidV4, String orgKey) {
    var entitlement = api.a3sAnalysisApi().getA3sOrgEntitlement(orgUuidV4);
    if (entitlement == null) {
      LOG.debug("Vortex entitlement check: could not retrieve A3S entitlement for org '" + orgKey + "'");
      return false;
    }
    if (!entitlement.hasEntitlement()) {
      LOG.info("Vortex is not licensed on this SonarQube Server. Ask your administrator.");
      return false;
    }
    return true;
  }

  private boolean isSaraEnabled(String orgUuidV4, String orgKey) {
    var enabled = api.wasFeatureFlagsApi().isAgenticReadinessAssessmentEnabled(orgUuidV4);
    LOG.debug("Agentic readiness feature flag for org '" + orgKey + "': " + enabled);
    return enabled;
  }
}
