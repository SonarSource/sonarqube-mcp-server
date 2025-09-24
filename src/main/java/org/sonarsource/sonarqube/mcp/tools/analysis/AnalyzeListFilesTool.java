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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class AnalyzeListFilesTool extends Tool {
  private static final McpLogger LOG = McpLogger.getInstance();

  public static final String TOOL_NAME = "analyze_list_files";
  public static final String LIST_FILES_PROPERTY = "list_files";

  private final SonarQubeIdeBridgeClient bridgeClient;

  public AnalyzeListFilesTool(SonarQubeIdeBridgeClient bridgeClient) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Analyze files in the current working directory using SonarQube for IDE. " +
        "This tool connects to a running SonarQube for IDE instance to perform code quality analysis on a list of files.")
      .addArrayProperty(LIST_FILES_PROPERTY, "string", "List of absolute file paths to analyze")
      .build());
    this.bridgeClient = bridgeClient;
  }

  @Override
  public Result execute(Arguments arguments) {
    if (!bridgeClient.isAvailable()) {
      return Result.failure("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.");
    }

    var listFiles = arguments.getOptionalStringList(LIST_FILES_PROPERTY);
    if (listFiles == null || listFiles.isEmpty()) {
      return Result.failure("No files provided to analyze. Please provide a list of file paths using the '" + LIST_FILES_PROPERTY + "' property.");
    }

    LOG.info("Starting SonarQube for IDE analysis");

    var analysisResult = bridgeClient.requestAnalyzeListFiles(listFiles);
    if (analysisResult.isEmpty()) {
      return Result.failure("Failed to request analysis of the list of files. Check logs for details.");
    }

    var results = analysisResult.get();
    var issuesSummary = formatAnalysisResults(results);

    LOG.info("Returning success result to MCP client");
    return Result.success(issuesSummary);
  }

  private static String formatAnalysisResults(SonarQubeIdeBridgeClient.AnalyzeListFilesResponse results) {
    var sb = new StringBuilder();

    sb.append("SonarQube for IDE Analysis Completed!\n\n");
    sb.append("Analysis Summary:\n");

    if (results.findings().isEmpty()) {
      sb.append("No findings found! Your code looks good.\n\n");
    } else {
      var findings = results.findings();
      sb.append("Issues Found (").append(findings.size()).append("):\n");
      // Show max 100 issues
      for (int i = 0; i < Math.min(100, findings.size()); i++) {
        var issue = findings.get(i);
        sb.append("  ").append(i + 1).append(". ").append(formatFinding(issue)).append("\n");
      }
      if (findings.size() > 100) {
        sb.append("  ... and ").append(findings.size() - 100).append(" more issues\n");
      }
    }

    sb.append("Next Steps:\n");
    sb.append("Check SonarQube for IDE - issues are now displayed in your extension\n");
    sb.append("Ask the agent to fix the issues.");

    return sb.toString();
  }

  private static String formatFinding(SonarQubeIdeBridgeClient.AnalyzeListFilesIssueResponse issue) {
    var textRange = issue.textRange();
    if (textRange == null) {
      return String.format("[%s] %s (file: %s)",
        issue.severity(), issue.message(), issue.filePath());
    } else {
      return String.format("[%s] %s (file: %s [Lines: %d to %d])",
        issue.severity(), issue.message(), issue.filePath(),
        issue.textRange().getStartLine(), issue.textRange().getEndLine());
    }
  }

}
