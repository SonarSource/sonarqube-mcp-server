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
package org.sonarsource.sonarqube.mcp.serverapi;

import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.duplications.DuplicationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.languages.LanguagesApi;
import org.sonarsource.sonarqube.mcp.serverapi.measures.MeasuresApi;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.MetricsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.PullRequestsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.sources.SourcesApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.views.ViewsApi;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.WebhooksApi;

public class ServerApi {

  private final ServerApiHelper helper;
  private final boolean isSonarCloud;

  public ServerApi(ServerApiHelper helper, boolean isSonarCloud) {
    this.helper = helper;
    this.isSonarCloud = isSonarCloud;
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
    return new PluginsApi(helper, isSonarCloud);
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

  public PullRequestsApi pullRequestsApi() {
    return new PullRequestsApi(helper);
  }

  public String getOrganization() {
    return helper.getOrganization();
  }

  public boolean isSonarQubeCloud() {
    return isSonarCloud;
  }

}
