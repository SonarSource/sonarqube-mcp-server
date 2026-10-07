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
package org.sonarsource.sonarqube.mcp.tools.onboarding;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.onboarding.CloudOnboardingApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;

public class GetCloudAnalysisStatusTool extends Tool {
  public static final String TOOL_NAME = "get_cloud_analysis_status";
  private final ServerApiProvider provider;

  public GetCloudAnalysisStatusTool(ServerApiProvider provider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Get Cloud Analysis Status")
      .setDescription("Check completed default-branch analysis; optionally enable eligible automatic analysis. Repeat while pending.")
      .addRequiredStringProperty("projectKey", "Cloud project key.")
      .addBooleanProperty("enableAutomaticAnalysis", "Enable automatic analysis if no completed analysis exists; defaults to false.")
      .build(), ToolCategory.PROJECTS);
    this.provider = provider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var key = arguments.getStringOrThrow("projectKey");
    var api = provider.getForOnboarding().cloudOnboardingApi();
    var analyses = api.analyses(key).analyses();
    var url = new UrlBuilder(api.serverUrl() + "/dashboard").addParam("id", key).build();
    if (!analyses.isEmpty()) {
      return Result.success(new Response("completed", key, analyses.getFirst(), null, url, null));
    }
    var eligibility = api.eligibility(key, Boolean.TRUE.equals(arguments.getOptionalBoolean("enableAutomaticAnalysis")));
    if (eligibility != null && !eligibility.eligible()) {
      return Result.success(new Response("ci_required", key, null, eligibility.ineligibilityReason(), url, null));
    }
    return Result.success(new Response("pending", key, null, null, url, 5));
  }

  public record Response(String status, String projectKey, CloudOnboardingApi.Analysis analysis, String reason, String url, Integer retryAfterSeconds) { }
}
