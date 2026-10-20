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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.WasFeatureFlagsApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.duplications.DuplicationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.languages.LanguagesApi;
import org.sonarsource.sonarqube.mcp.serverapi.measures.MeasuresApi;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.MetricsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.PullRequestsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.sources.SourcesApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.users.UsersApi;
import org.sonarsource.sonarqube.mcp.serverapi.views.ViewsApi;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.WebhooksApi;

public class ServerApi {

  private final ServerApiHelper helper;
  private final boolean isSonarQubeCloud;
  private final OrgFeatureEntitlements orgFeatureEntitlements;
  private final ConcurrentMap<String, String> organizationUuids;
  private final UsersApi usersApi;

  public ServerApi(ServerApiHelper helper, boolean isSonarQubeCloud) {
    this(helper, isSonarQubeCloud, null);
  }

  private ServerApi(ServerApiHelper helper, boolean isSonarQubeCloud, @Nullable ServerApi sessionApi) {
    this.helper = helper;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.organizationUuids = sessionApi == null ? new ConcurrentHashMap<>() : sessionApi.organizationUuids;
    this.usersApi = sessionApi == null ? new UsersApi(helper) : sessionApi.usersApi;
    this.orgFeatureEntitlements = sessionApi == null ? new OrgFeatureEntitlements(this) : sessionApi.orgFeatureEntitlements;
  }

  public QualityGatesApi qualityGatesApi() {
    return new QualityGatesApi(helper);
  }

  public QualityProfilesApi qualityProfilesApi() {
    return new QualityProfilesApi(helper);
  }

  public ComponentsApi componentsApi() {
    return new ComponentsApi(helper);
  }

  public IssuesApi issuesApi() {
    return new IssuesApi(helper);
  }

  public HotspotsApi hotspotsApi() {
    return new HotspotsApi(helper);
  }

  public RulesApi rulesApi() {
    return new RulesApi(helper);
  }

  public LanguagesApi languagesApi() {
    return new LanguagesApi(helper);
  }

  public MeasuresApi measuresApi() {
    return new MeasuresApi(helper);
  }

  public MetricsApi metricsApi() {
    return new MetricsApi(helper);
  }

  public SourcesApi sourcesApi() {
    return new SourcesApi(helper);
  }

  public DuplicationsApi duplicationsApi() {
    return new DuplicationsApi(helper);
  }

  public SystemApi systemApi() {
    return new SystemApi(helper);
  }

  public PluginsApi pluginsApi() {
    return new PluginsApi(helper, isSonarQubeCloud);
  }

  public ScaApi scaApi() {
    return new ScaApi(helper);
  }

  public WebhooksApi webhooksApi() {
    return new WebhooksApi(helper);
  }

  public ViewsApi viewsApi() {
    return new ViewsApi(helper);
  }

  public EnterprisesApi enterprisesApi() {
    return new EnterprisesApi(helper);
  }

  public FeaturesApi featuresApi() {
    return new FeaturesApi(helper);
  }

  public A3sAnalysisApi a3sAnalysisApi() {
    return new A3sAnalysisApi(helper);
  }

  public AgenticReadinessApi agenticReadinessApi() {
    return new AgenticReadinessApi(helper);
  }

  public WasFeatureFlagsApi wasFeatureFlagsApi() {
    return new WasFeatureFlagsApi(helper);
  }

  public CagApi cagApi() {
    return new CagApi(helper);
  }

  public PullRequestsApi pullRequestsApi() {
    return new PullRequestsApi(helper);
  }

  public ProjectBranchesApi projectBranchesApi() {
    return new ProjectBranchesApi(helper);
  }

  public UsersApi usersApi() {
    return usersApi;
  }

  public OrganizationsApi organizationsApi() {
    return new OrganizationsApi(helper);
  }

  public ArchitectureApi architectureApi() {
    return new ArchitectureApi(helper, () -> {
      var organization = getOrganization();
      return organization == null || organization.isBlank() ? null : resolveOrganizationUuid(ResolvedOrganization.fromKey(organization));
    });
  }

  public OrgFeatureEntitlements orgFeatureEntitlements() {
    return orgFeatureEntitlements;
  }

  public void cacheOrganizationUuid(ResolvedOrganization org) {
    var uuid = org.uuidV4();
    if (uuid != null && !uuid.isBlank()) {
      organizationUuids.putIfAbsent(org.key(), uuid);
    }
  }

  /** Shares successful lookups within a stdio session or HTTP request; failures remain retryable. */
  @Nullable
  public String resolveOrganizationUuid(ResolvedOrganization org) {
    return organizationUuids.computeIfAbsent(org.key(), key -> {
      var uuid = org.uuidV4() != null ? org.uuidV4() : organizationsApi().getOrganizationUuidV4(key);
      return uuid == null || uuid.isBlank() ? null : uuid;
    });
  }

  public ServerApi withOrganization(String organization) {
    // Selections share stdio session caches. HTTP starts with a fresh ServerApi for every request.
    return isSonarQubeCloud ? new ServerApi(helper.withOrganization(organization), true, this) : this;
  }

  public void requireOrganization() {
    if (isSonarQubeCloud && (getOrganization() == null || getOrganization().isBlank())) {
      throw new IllegalArgumentException("Organization is required. Call list_organizations first, then pass its key as organization, or configure SONARQUBE_ORG.");
    }
  }

  @Nullable
  public String getOrganization() {
    return helper.getOrganization();
  }

  public boolean isSonarQubeCloud() {
    return isSonarQubeCloud;
  }

}
