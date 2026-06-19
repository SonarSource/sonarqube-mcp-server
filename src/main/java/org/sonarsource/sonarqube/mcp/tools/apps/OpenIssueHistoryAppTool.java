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
package org.sonarsource.sonarqube.mcp.tools.apps;

import io.modelcontextprotocol.spec.McpSchema;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.sonarsource.sonarqube.mcp.apps.IssueHistoryApp;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.response.IssueCountHistoryResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;
import org.sonarsource.sonarqube.mcp.tools.issues.GetIssueCountHistoryTool;
import org.sonarsource.sonarqube.mcp.tools.issues.GetIssueCountHistoryToolResponse;

public class OpenIssueHistoryAppTool extends Tool {

  public static final String TOOL_NAME = "issue_history_app";
  private static final String[] VALID_SEVERITIES = {"BLOCKER", "HIGH", "INFO", "LOW", "MEDIUM"};
  private static final String[] VALID_ISSUE_TYPES = {"BUG", "CODE_SMELL", "SECURITY_HOTSPOT", "VULNERABILITY"};
  private static final String[] VALID_STATUSES = {"ACCEPTED", "CONFIRMED", "FALSE_POSITIVE", "FIXED", "OPEN", "REVIEWED", "SAFE", "TO_REVIEW"};
  private static final String[] VALID_SLICE_BY = {"RULE_KEY", "SEVERITY", "SOFTWARE_QUALITY", "STATUS", "TYPE"};

  private final ServerApiProvider serverApiProvider;
  private final HistoryTargetResolver targetResolver;

  public OpenIssueHistoryAppTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(OpenIssueHistoryAppToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Open Issue History MCP App")
      .setDescription("Open a basic MCP app table for SonarQube Cloud issue count history.")
      .setMeta(IssueHistoryApp.toolDescriptorMeta())
      .addRequiredEnumValueProperty(HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.VALID_TARGET_TYPES,
        "Target entity type: PROJECT_BRANCH or PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PROJECT_KEY_PROPERTY, "Project key used when targetType is PROJECT_BRANCH")
      .addStringProperty(HistoryTargetResolver.BRANCH_PROPERTY, "Branch name used when targetType is PROJECT_BRANCH; defaults to the main branch")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "Portfolio UUID used when targetType is PORTFOLIO")
      .addStringProperty(HistoryTargetResolver.PORTFOLIO_NAME_PROPERTY, "Exact portfolio name used when targetType is PORTFOLIO and portfolioId is not provided")
      .addStringProperty(HistoryTargetResolver.ENTERPRISE_ID_PROPERTY, "Enterprise UUID used to resolve portfolioName; if omitted, favorite portfolios are searched")
      .addRequiredStringProperty(GetIssueCountHistoryTool.START_DATE_PROPERTY, "Inclusive start of the date range")
      .addStringProperty(GetIssueCountHistoryTool.END_DATE_PROPERTY, "Inclusive end of the date range; defaults to current date")
      .addArrayProperty(GetIssueCountHistoryTool.IMPACTS_PROPERTY, "string", "Impacts to filter by, such as MAINTAINABILITY:HIGH or SECURITY:MEDIUM")
      .addEnumProperty(GetIssueCountHistoryTool.ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES, "Issue types to filter by")
      .addArrayProperty(GetIssueCountHistoryTool.RULE_KEYS_PROPERTY, "string", "Rule keys to filter by")
      .addEnumProperty(GetIssueCountHistoryTool.SEVERITIES_PROPERTY, VALID_SEVERITIES, "Issue severities to filter by")
      .addEnumValueProperty(GetIssueCountHistoryTool.SLICE_BY_PROPERTY, VALID_SLICE_BY, "Dimension used to group issue counts")
      .addEnumProperty(GetIssueCountHistoryTool.STATUSES_PROPERTY, VALID_STATUSES, "Issue statuses to filter by")
      .setReadOnlyHint()
      .build(),
      ToolCategory.APPS);
    this.serverApiProvider = serverApiProvider;
    this.targetResolver = new HistoryTargetResolver(serverApiProvider);
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var target = targetResolver.resolve(arguments);
    if (target.isFailure()) {
      return target.failure();
    }

    var response = serverApiProvider.get().organizationsHistoryApi().getIssueCountHistory(
      target.entityId(),
      target.entityType(),
      normalizeStartDate(arguments.getStringOrThrow(GetIssueCountHistoryTool.START_DATE_PROPERTY)),
      arguments.getOptionalString(GetIssueCountHistoryTool.END_DATE_PROPERTY),
      arguments.getOptionalStringList(GetIssueCountHistoryTool.IMPACTS_PROPERTY),
      arguments.getOptionalEnumList(GetIssueCountHistoryTool.ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES),
      arguments.getOptionalStringList(GetIssueCountHistoryTool.RULE_KEYS_PROPERTY),
      arguments.getOptionalEnumList(GetIssueCountHistoryTool.SEVERITIES_PROPERTY, VALID_SEVERITIES),
      arguments.getOptionalEnumValue(GetIssueCountHistoryTool.SLICE_BY_PROPERTY, VALID_SLICE_BY),
      arguments.getOptionalEnumList(GetIssueCountHistoryTool.STATUSES_PROPERTY, VALID_STATUSES)
    );
    var issueHistory = buildStructuredContent(response);
    IssueHistoryApp.remember(issueHistory);

    return new Tool.Result(McpSchema.CallToolResult.builder()
      .isError(false)
      .addContent(IssueHistoryApp.embeddedResource(issueHistory))
      .addContent(IssueHistoryApp.resourceLink())
      .addTextContent("Opened the SonarQube Issue History MCP app.")
      .structuredContent(new OpenIssueHistoryAppToolResponse(IssueHistoryApp.RESOURCE_URI, issueHistory))
      .meta(IssueHistoryApp.toolDescriptorMeta())
      .build());
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

  private static String normalizeStartDate(String startDate) {
    try {
      return LocalDate.parse(startDate)
        .atStartOfDay(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_INSTANT);
    } catch (DateTimeParseException ignored) {
      return startDate;
    }
  }

}
