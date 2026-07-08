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
package org.sonarsource.sonarqube.mcp.tools.metrics;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.response.SearchMetricsResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;

public class SearchMetricsTool extends Tool {

  public static final String TOOL_NAME = "search_metrics";
  public static final String PAGE_INDEX_PROPERTY = ToolParameters.PAGE_INDEX;
  public static final String PAGE_SIZE_PROPERTY = ToolParameters.PAGE_SIZE;

  private final ServerApiProvider serverApiProvider;

  public SearchMetricsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchMetricsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search SonarQube Metrics")
      .setDescription("Search for available metrics")
      .addNumberProperty(PAGE_INDEX_PROPERTY, "1-based page index (default: 1)")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "Page size. Must be greater than 0 and less than or equal to 500 (default: 100)")
      .setReadOnlyHint()
      .build(),
      ToolCategory.MEASURES);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var page = arguments.getOptionalPageIndex();
    var pageSize = arguments.getOptionalPageSize();
    
    var response = serverApiProvider.get().metricsApi().searchMetrics(page, pageSize);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static SearchMetricsToolResponse buildStructuredContent(SearchMetricsResponse response) {
    var metrics = response.metrics() == null ? java.util.List.<SearchMetricsToolResponse.Metric>of() : response.metrics().stream()
      .map(m -> new SearchMetricsToolResponse.Metric(
        m.id(), m.key(), m.name(), m.description(), m.domain(), m.type(),
        m.hidden(), m.custom()
      ))
      .toList();

    return new SearchMetricsToolResponse(metrics, response.total(), response.p(), response.ps());
  }

}
