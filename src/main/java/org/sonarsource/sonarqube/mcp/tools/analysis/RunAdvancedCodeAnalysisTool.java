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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.response.AnalysisResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class RunAdvancedCodeAnalysisTool extends Tool {

  public static final String TOOL_NAME = "run_advanced_code_analysis";

  public static final String ORGANIZATION_ID_PROPERTY = "organizationId";
  public static final String ORGANIZATION_KEY_PROPERTY = "organizationKey";
  public static final String PROJECT_ID_PROPERTY = "projectId";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_ID_PROPERTY = "branchId";
  public static final String BRANCH_NAME_PROPERTY = "branchName";
  public static final String FILE_PATH_PROPERTY = "filePath";
  public static final String FILE_CONTENT_PROPERTY = "fileContent";
  public static final String PATCH_CONTENT_PROPERTY = "patchContent";
  public static final String FILE_SCOPE_PROPERTY = "fileScope";

  private final ServerApiProvider serverApiProvider;

  public RunAdvancedCodeAnalysisTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(RunAdvancedCodeAnalysisToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Advanced Code Analysis")
      .setDescription("Run advanced code analysis remotely using SonarQube Cloud advanced analysis. " +
        "Analyzes a single file and optionally a patch to detect new, matched, and closed issues.")
      .addStringProperty(ORGANIZATION_ID_PROPERTY, "The unique identifier (UUID) of the organization. Either organizationId or organizationKey is required.")
      .addStringProperty(ORGANIZATION_KEY_PROPERTY, "The key of the organization. Either organizationId or organizationKey is required.")
      .addStringProperty(PROJECT_ID_PROPERTY, "The unique identifier (UUID) of the project. Either projectId or projectKey is required.")
      .addStringProperty(PROJECT_KEY_PROPERTY, "The key of the project. Either projectId or projectKey is required.")
      .addStringProperty(BRANCH_ID_PROPERTY, "The unique identifier of the branch to retrieve the latest analysis context.")
      .addStringProperty(BRANCH_NAME_PROPERTY, "The branch name to retrieve the latest analysis context.")
      .addRequiredStringProperty(FILE_PATH_PROPERTY, "Project-relative path of the file to analyze (e.g., 'src/main/java/MyClass.java').")
      .addRequiredStringProperty(FILE_CONTENT_PROPERTY, "The original content of the file to analyze.")
      .addStringProperty(PATCH_CONTENT_PROPERTY, "The patch content to apply (unified diff format).")
      .addStringProperty(FILE_SCOPE_PROPERTY, "Defines in which scope the file originates from: 'MAIN' or 'TEST'. Defaults to 'MAIN'.")
      .setReadOnlyHint()
      .build(),
      ToolCategory.ANALYSIS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    validateRequiredIdentifiers(arguments);
    var request = extractRequest(arguments);
    var response = serverApiProvider.get().a3sAnalysisHubApi().analyze(request);
    var toolResponse = buildStructuredContent(response);
    return Result.success(toolResponse);
  }

  private static void validateRequiredIdentifiers(Arguments arguments) {
    var orgId = arguments.getOptionalString(ORGANIZATION_ID_PROPERTY);
    var orgKey = arguments.getOptionalString(ORGANIZATION_KEY_PROPERTY);
    if (orgId == null && orgKey == null) {
      throw new IllegalArgumentException("Either organizationId or organizationKey is required");
    }

    var projectId = arguments.getOptionalString(PROJECT_ID_PROPERTY);
    var projectKey = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    if (projectId == null && projectKey == null) {
      throw new IllegalArgumentException("Either projectId or projectKey is required");
    }
  }

  private static AnalysisCreationRequest extractRequest(Arguments arguments) {
    return new AnalysisCreationRequest(
      arguments.getOptionalString(ORGANIZATION_ID_PROPERTY),
      arguments.getOptionalString(ORGANIZATION_KEY_PROPERTY),
      arguments.getOptionalString(PROJECT_ID_PROPERTY),
      arguments.getOptionalString(PROJECT_KEY_PROPERTY),
      arguments.getOptionalString(BRANCH_ID_PROPERTY),
      arguments.getOptionalString(BRANCH_NAME_PROPERTY),
      arguments.getStringOrThrow(FILE_PATH_PROPERTY),
      arguments.getStringOrThrow(FILE_CONTENT_PROPERTY),
      arguments.getOptionalString(PATCH_CONTENT_PROPERTY),
      arguments.getOptionalString(FILE_SCOPE_PROPERTY)
    );
  }

  private static RunAdvancedCodeAnalysisToolResponse buildStructuredContent(AnalysisResponse response) {
    var issues = response.issues().stream()
      .map(RunAdvancedCodeAnalysisTool::mapIssue)
      .toList();

    RunAdvancedCodeAnalysisToolResponse.PatchResult patchResult = null;
    if (response.patchResult() != null) {
      patchResult = new RunAdvancedCodeAnalysisToolResponse.PatchResult(
        response.patchResult().newIssues().stream().map(RunAdvancedCodeAnalysisTool::mapIssue).toList(),
        response.patchResult().matchedIssues().stream().map(RunAdvancedCodeAnalysisTool::mapIssue).toList(),
        response.patchResult().closedIssues()
      );
    }

    return new RunAdvancedCodeAnalysisToolResponse(issues, patchResult);
  }

  private static RunAdvancedCodeAnalysisToolResponse.Issue mapIssue(AnalysisResponse.Issue issue) {
    RunAdvancedCodeAnalysisToolResponse.TextRange textRange = null;
    if (issue.textRange() != null) {
      textRange = new RunAdvancedCodeAnalysisToolResponse.TextRange(
        issue.textRange().startLine(),
        issue.textRange().endLine()
      );
    }

    List<RunAdvancedCodeAnalysisToolResponse.Flow> flows = null;
    if (issue.flows() != null && !issue.flows().isEmpty()) {
      flows = issue.flows().stream()
        .map(RunAdvancedCodeAnalysisTool::mapFlow)
        .toList();
    }

    return new RunAdvancedCodeAnalysisToolResponse.Issue(
      issue.id(),
      issue.filePath(),
      issue.message(),
      issue.rule(),
      textRange,
      flows
    );
  }

  private static RunAdvancedCodeAnalysisToolResponse.Flow mapFlow(AnalysisResponse.Flow flow) {
    var locations = flow.locations().stream()
      .map(RunAdvancedCodeAnalysisTool::mapLocation)
      .toList();

    return new RunAdvancedCodeAnalysisToolResponse.Flow(
      flow.type(),
      flow.description(),
      locations
    );
  }

  private static RunAdvancedCodeAnalysisToolResponse.Location mapLocation(AnalysisResponse.Location location) {
    RunAdvancedCodeAnalysisToolResponse.TextRange textRange = null;
    if (location.textRange() != null) {
      textRange = new RunAdvancedCodeAnalysisToolResponse.TextRange(
        location.textRange().startLine(),
        location.textRange().endLine()
      );
    }

    return new RunAdvancedCodeAnalysisToolResponse.Location(
      textRange,
      location.message(),
      location.file()
    );
  }
}
