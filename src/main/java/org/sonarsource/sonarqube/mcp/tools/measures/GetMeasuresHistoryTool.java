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

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.response.MeasuresHistoryResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;

public class GetMeasuresHistoryTool extends Tool {

  public static final String TOOL_NAME = "get_measures_history";
  public static final String METRIC_KEYS_PROPERTY = "metricKeys";
  public static final String START_DATE_PROPERTY = "startDate";
  public static final String END_DATE_PROPERTY = "endDate";
  private static final String DATE_FORMAT_DESCRIPTION = "Use ISO-8601 UTC timestamps in the format YYYY-MM-DDThh:mm:ssZ, " +
    "for example 2026-06-03T00:00:00Z";

  private final ServerApiProvider serverApiProvider;
  private final HistoryTargetResolver targetResolver;

  public GetMeasuresHistoryTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetMeasuresHistoryToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Measures History")
      .setDescription("Get SonarQube Cloud measures history for a project branch or portfolio. " +
        "For PROJECT_BRANCH, provide projectKey and optionally branch; if branch is omitted, the main branch is used. " +
        "For PORTFOLIO, provide portfolioId or an exact portfolioName; when multiple portfolios match, retry with portfolioId.")
      .addRequiredEnumValueProperty(HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.VALID_TARGET_TYPES,
        "Target entity type: PROJECT_BRANCH or PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PROJECT_KEY_PROPERTY, "Project key used when targetType is PROJECT_BRANCH")
      .addStringProperty(HistoryTargetResolver.BRANCH_PROPERTY, "Branch name used when targetType is PROJECT_BRANCH; defaults to the main branch")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "Portfolio UUID used when targetType is PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_NAME_PROPERTY, "Exact portfolio name used when targetType is PORTFOLIO and portfolioId is not provided")
      .addStringProperty(HistoryTargetResolver.ENTERPRISE_ID_PROPERTY, "Enterprise UUID used to resolve portfolioName; if omitted, favorite portfolios are searched")
      .addRequiredArrayProperty(METRIC_KEYS_PROPERTY, "string", "Metric keys to retrieve, such as coverage or bugs")
      .addRequiredStringProperty(START_DATE_PROPERTY, "Inclusive start of the date range. " + DATE_FORMAT_DESCRIPTION)
      .addStringProperty(END_DATE_PROPERTY, "Inclusive end of the date range; defaults to current date. " + DATE_FORMAT_DESCRIPTION)
      .setReadOnlyHint()
      .build(),
      ToolCategory.MEASURES);
    this.serverApiProvider = serverApiProvider;
    this.targetResolver = new HistoryTargetResolver(serverApiProvider);
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var target = targetResolver.resolve(arguments);
    if (target.isFailure()) {
      return target.failure();
    }

    var metricKeys = arguments.getOptionalStringList(METRIC_KEYS_PROPERTY);
    if (metricKeys == null) {
      return Tool.Result.failure("Missing required argument: " + METRIC_KEYS_PROPERTY);
    }
    var startDate = arguments.getStringOrThrow(START_DATE_PROPERTY);
    var endDate = arguments.getOptionalString(END_DATE_PROPERTY);

    var response = serverApiProvider.get().organizationsHistoryApi().getMeasuresHistory(
      target.entityType(), target.entityId(), metricKeys, startDate, endDate);
    return Tool.Result.success(buildStructuredContent(response));
  }

  private static GetMeasuresHistoryToolResponse buildStructuredContent(MeasuresHistoryResponse response) {
    var history = response.measuresHistory() == null ? List.<GetMeasuresHistoryToolResponse.MeasuresHistory>of() :
      response.measuresHistory().stream()
        .map(item -> new GetMeasuresHistoryToolResponse.MeasuresHistory(
          item.date(),
          item.measures() == null ? List.of() : item.measures().stream()
            .map(measure -> new GetMeasuresHistoryToolResponse.Measure(measure.metric(), measure.type(), measure.value()))
            .toList()
        ))
        .toList();
    return new GetMeasuresHistoryToolResponse(history);
  }
}
