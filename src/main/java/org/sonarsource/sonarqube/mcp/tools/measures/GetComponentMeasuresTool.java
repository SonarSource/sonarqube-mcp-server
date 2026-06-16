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
package org.sonarsource.sonarqube.mcp.tools.measures;

import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetComponentMeasuresTool extends Tool {

  public static final String TOOL_NAME = "get_component_measures";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = BranchPullRequestContext.BRANCH_PROPERTY;
  public static final String METRIC_KEYS_PROPERTY = "metricKeys";
  public static final String PULL_REQUEST_PROPERTY = BranchPullRequestContext.PULL_REQUEST_PROPERTY;

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public GetComponentMeasuresTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(GetComponentMeasuresToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Project Measures")
      .setDescription("Get SonarQube measures for a project, such as ncloc, complexity, violations, coverage, etc.")
      .addProjectKeyProperty(PROJECT_KEY_PROPERTY, configuredProjectKey)
      .addBranchAndPullRequestProperties()
      .addArrayProperty(METRIC_KEYS_PROPERTY, "string", "The metric keys to retrieve (e.g. ncloc, complexity, violations, coverage)")
      .setReadOnlyHint()
      .build(),
      ToolCategory.MEASURES);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var component = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);
    var branchPullRequest = BranchPullRequestContext.from(arguments);
    var metricKeys = arguments.getOptionalStringList(METRIC_KEYS_PROPERTY);

    var validationError = branchPullRequest.validationError();
    if (validationError.isPresent()) {
      return validationError.get();
    }

    var response = serverApiProvider.get().measuresApi().getComponentMeasures(
      component, branchPullRequest.branch(), metricKeys, branchPullRequest.pullRequest());
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static GetComponentMeasuresToolResponse buildStructuredContent(ComponentMeasuresResponse response) {
    var comp = response.component();
    
    // Handle null component case
    if (comp == null) {
      // Return minimal valid response when no component found
      var emptyComponent = new GetComponentMeasuresToolResponse.Component(
        "", "", "", null, null, null
      );
      return new GetComponentMeasuresToolResponse(emptyComponent, List.of(), null);
    }
    
    var componentResponse = new GetComponentMeasuresToolResponse.Component(
      comp.key(), comp.name(), comp.qualifier(),
      comp.description(), comp.language(), comp.path()
    );
    
    var measures = (comp.measures() != null) ?
      comp.measures().stream()
        .map(m -> new GetComponentMeasuresToolResponse.Measure(m.metric(), m.value()))
        .toList()
      : List.<GetComponentMeasuresToolResponse.Measure>of();
    
    List<GetComponentMeasuresToolResponse.Metric> metrics = null;
    if (response.metrics() != null) {
      metrics = response.metrics().stream()
        .map(m -> new GetComponentMeasuresToolResponse.Metric(
          m.key(), m.name(), m.description(), m.domain(), m.type(),
          m.hidden(), m.custom()
        ))
        .toList();
    }

    return new GetComponentMeasuresToolResponse(componentResponse, measures, metrics);
  }

}
