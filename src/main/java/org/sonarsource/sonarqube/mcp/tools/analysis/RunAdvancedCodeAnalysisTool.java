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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.response.AnalysisResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class RunAdvancedCodeAnalysisTool extends Tool {

  private static final McpLogger LOG = McpLogger.getInstance();

  public static final String TOOL_NAME = "run_advanced_code_analysis";

  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_NAME_PROPERTY = "branchName";
  public static final String FILE_PATH_PROPERTY = "filePath";
  public static final String FILE_CONTENT_PROPERTY = "fileContent";
  public static final String FILE_SCOPE_PROPERTY = "fileScope";
  
  private static final String[] VALID_FILE_SCOPES = {"MAIN", "TEST"};

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;
  private final Path configuredWorkspacePath;

  public RunAdvancedCodeAnalysisTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey, Path configuredWorkspacePath) {
    super(buildSchema(configuredProjectKey), ToolCategory.ANALYSIS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
    this.configuredWorkspacePath = configuredWorkspacePath;
  }

  private static McpSchema.Tool buildSchema(@Nullable String configuredProjectKey) {
    var builder = SchemaToolBuilder.forOutput(RunAdvancedCodeAnalysisToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("SonarQube Advanced Code Analysis")
      .setDescription("Run advanced code analysis on a single file using SonarQube Cloud's server-side engine. " +
        "Identifies code quality and security issues, leveraging the project's full analysis context for deeper cross-file detection. " +
        "Always specify the file scope (MAIN or TEST) for more accurate results.")
      .addProjectKeyProperty(PROJECT_KEY_PROPERTY, "The key of the project.", configuredProjectKey)
      .addRequiredStringProperty(BRANCH_NAME_PROPERTY, "The branch name used to retrieve the latest analysis context from SonarQube Cloud.")
      .addRequiredStringProperty(FILE_PATH_PROPERTY, "Project-relative path of the file to analyze (e.g., 'src/main/java/MyClass.java').");

    return builder
      .addEnumProperty(FILE_SCOPE_PROPERTY, VALID_FILE_SCOPES, "Scope of the file: MAIN or TEST (default: MAIN).")
      .setReadOnlyHint()
      .build();
  }

  public static boolean isA3sEnabled(ServerApi api, String orgKey) {
    var orgUuidV4 = api.organizationsApi().getOrganizationUuidV4(orgKey);
    if (orgUuidV4 == null) {
      LOG.debug("A3S entitlement check: could not resolve UUID for org '" + orgKey + "' - falling back to standard analysis");
      return false;
    }
    var config = api.a3sAnalysisApi().getA3sOrgConfig(orgUuidV4);
    if (config == null) {
      LOG.debug("A3S entitlement check: could not retrieve org config for org '" + orgKey + "' - falling back to standard analysis");
      return false;
    }
    if (!config.enabled()) {
      LOG.debug("A3S entitlement check: advanced analysis is not enabled for org '" + orgKey + "'");
    }
    return config.enabled();
  }

  @Override
  public Result execute(Arguments arguments) {
    var serverApi = serverApiProvider.get();
    var organizationKey = serverApi.getOrganization();
    if (organizationKey == null) {
      throw new IllegalStateException("run_advanced_code_analysis requires an organization to be configured in MCP (SONARQUBE_ORG).");
    }

    String fileContent;
    try {
      fileContent = Tool.resolveFileContent(configuredWorkspacePath, arguments, FILE_PATH_PROPERTY, FILE_CONTENT_PROPERTY);
    } catch (IOException e) {
      return Result.failure("Could not read file: " + e.getMessage());
    } catch (IllegalArgumentException e) {
      return Result.failure(e.getMessage());
    }

    var scope = arguments.getEnumOrDefault(FILE_SCOPE_PROPERTY, VALID_FILE_SCOPES, "MAIN");
    var request = extractRequest(arguments, organizationKey, scope, fileContent);
    var response = serverApi.a3sAnalysisApi().analyze(request);
    var toolResponse = buildStructuredContent(response);
    return Result.success(toolResponse);
  }

  private AnalysisCreationRequest extractRequest(Arguments arguments, String organizationKey, String scope, String fileContent) {
    return new AnalysisCreationRequest(
      organizationKey,
      arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey),
      arguments.getStringOrThrow(BRANCH_NAME_PROPERTY),
      arguments.getStringOrThrow(FILE_PATH_PROPERTY),
      fileContent,
      scope
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

    List<RunAdvancedCodeAnalysisToolResponse.AnalysisError> analysisErrors = null;
    if (response.errors() != null && !response.errors().isEmpty()) {
      analysisErrors = response.errors().stream()
        .map(e -> new RunAdvancedCodeAnalysisToolResponse.AnalysisError(e.code(), e.message()))
        .toList();
    }

    return new RunAdvancedCodeAnalysisToolResponse(issues, patchResult, analysisErrors);
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
    List<RunAdvancedCodeAnalysisToolResponse.Location> locations = null;
    if (flow.locations() != null && !flow.locations().isEmpty()) {
      locations = flow.locations().stream()
        .map(RunAdvancedCodeAnalysisTool::mapLocation)
        .toList();
    }

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
