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
package org.sonarsource.sonarqube.mcp.tools.issues;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.response.IssueCountHistoryResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;

public class GetIssueCountHistoryTool extends Tool {

  public static final String TOOL_NAME = "get_issue_count_history";
  public static final String START_DATE_PROPERTY = "startDate";
  public static final String END_DATE_PROPERTY = "endDate";
  public static final String IMPACTS_PROPERTY = "impacts";
  public static final String ISSUE_TYPES_PROPERTY = "issueTypes";
  public static final String RULE_KEYS_PROPERTY = "ruleKeys";
  public static final String SEVERITIES_PROPERTY = "severities";
  public static final String SLICE_BY_PROPERTY = "sliceBy";
  public static final String STATUSES_PROPERTY = "statuses";
  private static final String DATE_FORMAT_DESCRIPTION = "Use ISO-8601 UTC timestamps in the format YYYY-MM-DDThh:mm:ssZ, " +
    "for example 2026-06-03T00:00:00Z";

  private static final String[] VALID_SEVERITIES = {"BLOCKER", "HIGH", "INFO", "LOW", "MEDIUM"};
  private static final String[] VALID_ISSUE_TYPES = {"BUG", "CODE_SMELL", "SECURITY_HOTSPOT", "VULNERABILITY"};
  private static final String[] VALID_STATUSES = {"ACCEPTED", "CONFIRMED", "FALSE_POSITIVE", "FIXED", "OPEN", "REVIEWED", "SAFE", "TO_REVIEW"};
  private static final String[] VALID_SLICE_BY = {"RULE_KEY", "SEVERITY", "SOFTWARE_QUALITY", "STATUS", "TYPE"};

  private final ServerApiProvider serverApiProvider;
  private final HistoryTargetResolver targetResolver;

  public GetIssueCountHistoryTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetIssueCountHistoryToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Issue Count History")
      .setDescription("Get SonarQube Cloud issue count history for a project branch or portfolio. " +
        "For PROJECT_BRANCH, provide projectKey and optionally branch; if branch is omitted, the main branch is used. " +
        "For PORTFOLIO, provide portfolioId or an exact portfolioName; when multiple portfolios match, retry with portfolioId.")
      .addRequiredEnumValueProperty(HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.VALID_TARGET_TYPES,
        "Target entity type: PROJECT_BRANCH or PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PROJECT_KEY_PROPERTY, "Project key used when targetType is PROJECT_BRANCH")
      .addStringProperty(HistoryTargetResolver.BRANCH_PROPERTY, "Branch name used when targetType is PROJECT_BRANCH; defaults to the main branch")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "Portfolio UUID used when targetType is PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_NAME_PROPERTY, "Exact portfolio name used when targetType is PORTFOLIO and portfolioId is not provided")
      .addStringProperty(HistoryTargetResolver.ENTERPRISE_ID_PROPERTY, "Enterprise UUID used to resolve portfolioName; if omitted, favorite portfolios are searched")
      .addRequiredStringProperty(START_DATE_PROPERTY, "Inclusive start of the date range. " + DATE_FORMAT_DESCRIPTION)
      .addStringProperty(END_DATE_PROPERTY, "Inclusive end of the date range; defaults to current date. " + DATE_FORMAT_DESCRIPTION)
      .addArrayProperty(IMPACTS_PROPERTY, "string", "Impacts to filter by, such as MAINTAINABILITY:HIGH or SECURITY:MEDIUM")
      .addEnumProperty(ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES, "Issue types to filter by")
      .addArrayProperty(RULE_KEYS_PROPERTY, "string", "Rule keys to filter by")
      .addEnumProperty(SEVERITIES_PROPERTY, VALID_SEVERITIES, "Issue severities to filter by")
      .addEnumValueProperty(SLICE_BY_PROPERTY, VALID_SLICE_BY, "Dimension used to group issue counts")
      .addEnumProperty(STATUSES_PROPERTY, VALID_STATUSES, "Issue statuses to filter by")
      .setReadOnlyHint()
      .build(),
      ToolCategory.ISSUES);
    this.serverApiProvider = serverApiProvider;
    this.targetResolver = new HistoryTargetResolver(serverApiProvider);
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var target = targetResolver.resolve(arguments);
    if (target.isFailure()) {
      return target.failure();
    }

    var startDate = arguments.getStringOrThrow(START_DATE_PROPERTY);
    var endDate = arguments.getOptionalString(END_DATE_PROPERTY);
    var impacts = arguments.getOptionalStringList(IMPACTS_PROPERTY);
    var issueTypes = arguments.getOptionalEnumList(ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES);
    var ruleKeys = arguments.getOptionalStringList(RULE_KEYS_PROPERTY);
    var severities = arguments.getOptionalEnumList(SEVERITIES_PROPERTY, VALID_SEVERITIES);
    var sliceBy = arguments.getOptionalEnumValue(SLICE_BY_PROPERTY, VALID_SLICE_BY);
    var statuses = arguments.getOptionalEnumList(STATUSES_PROPERTY, VALID_STATUSES);

    var response = serverApiProvider.get().organizationsHistoryApi().getIssueCountHistory(
      target.entityId(), target.entityType(), startDate, endDate, impacts, issueTypes, ruleKeys, severities, sliceBy, statuses);
    return Tool.Result.success(buildStructuredContent(response));
  }

  private static GetIssueCountHistoryToolResponse buildStructuredContent(IssueCountHistoryResponse response) {
    var history = response.issueCountHistory() == null ? List.<GetIssueCountHistoryToolResponse.IssueCountHistory>of() :
      response.issueCountHistory().stream()
        .map(item -> new GetIssueCountHistoryToolResponse.IssueCountHistory(
          item.date(),
          item.distribution() == null ? List.of() : item.distribution().stream()
            .map(distribution -> new GetIssueCountHistoryToolResponse.Distribution(distribution.key(), distribution.value()))
            .toList()
        ))
        .toList();
    return new GetIssueCountHistoryToolResponse(history);
  }
}
