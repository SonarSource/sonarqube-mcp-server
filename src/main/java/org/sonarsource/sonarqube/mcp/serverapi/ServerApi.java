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
package org.sonarsource.sonarqube.mcp.serverapi;

import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.languages.LanguagesApi;
import org.sonarsource.sonarqube.mcp.serverapi.measures.MeasuresApi;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.MetricsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.settings.SettingsApi;
import org.sonarsource.sonarqube.mcp.serverapi.sources.SourcesApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.WebhooksApi;

public class ServerApi {

  private final ServerApiHelper helper;

  public ServerApi(ServerApiHelper helper) {
    this.helper = helper;
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
    return new IssuesApi(helper, helper.getOrganization());
  }

  public RulesApi rulesApi() {
    return new RulesApi(helper, helper.getOrganization());
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

  public SystemApi systemApi() {
    return new SystemApi(helper);
  }

  public PluginsApi pluginsApi() {
    return new PluginsApi(helper);
  }

  public ScaApi scaApi() {
    return new ScaApi(helper);
  }

  public SettingsApi settingsApi() {
    return new SettingsApi(helper);
  }

  public WebhooksApi webhooksApi() {
    return new WebhooksApi(helper, helper.getOrganization());
  }

  public EnterprisesApi enterprisesApi() {
    return new EnterprisesApi(helper);
  }

  public boolean isSonarQubeCloud() {
    return helper.getOrganization() != null;
  }
}
