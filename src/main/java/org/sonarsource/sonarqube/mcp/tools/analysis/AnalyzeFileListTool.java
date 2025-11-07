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

public class AnalyzeFileListTool extends Tool {
  private static final McpLogger LOG = McpLogger.getInstance();

  public static final String TOOL_NAME = "analyze_file_list";
  public static final String FILE_ABSOLUTE_PATHS_PROPERTY = "file_absolute_paths";

  private final SonarQubeIdeBridgeClient bridgeClient;

  public AnalyzeFileListTool(SonarQubeIdeBridgeClient bridgeClient) {
    super(SchemaToolBuilder.forOutput(AnalyzeFileListToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Multiple File Analysis")
      .setDescription("Analyze files in the current working directory using SonarQube for IDE. " +
        "This tool connects to a running SonarQube for IDE instance to perform code quality analysis on a list of files.")
      .addArrayProperty(FILE_ABSOLUTE_PATHS_PROPERTY, "string", "List of absolute file paths to analyze")
      .build());
    this.bridgeClient = bridgeClient;
  }

  @Override
  public Result execute(Arguments arguments) {
    if (!bridgeClient.isAvailable()) {
      return Result.failure("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.");
    }

    var fileAbsolutePaths = arguments.getStringListOrThrow(FILE_ABSOLUTE_PATHS_PROPERTY);
    if (fileAbsolutePaths.isEmpty()) {
      return Result.failure("No files provided to analyze. Please provide a list of file paths using the '" + FILE_ABSOLUTE_PATHS_PROPERTY + "' property.");
    }

    LOG.info("Starting SonarQube for IDE analysis");

    var analysisResult = bridgeClient.requestAnalyzeFileList(fileAbsolutePaths);
    if (analysisResult.isEmpty()) {
      return Result.failure("Failed to request analysis of the list of files. Check logs for details.");
    }

    var results = analysisResult.get();
    var toolResponse = buildStructuredContent(results);

    LOG.info("Returning success result to MCP client");
    return Result.success(toolResponse);
  }

  private static AnalyzeFileListToolResponse buildStructuredContent(SonarQubeIdeBridgeClient.AnalyzeFileListResponse results) {
    var findings = results.findings().stream()
      .map(f -> {
        AnalyzeFileListToolResponse.TextRange textRange = null;
        if (f.textRange() != null) {
          textRange = new AnalyzeFileListToolResponse.TextRange(
            f.textRange().getStartLine(),
            f.textRange().getEndLine()
          );
        }
        return new AnalyzeFileListToolResponse.Finding(f.severity(), f.message(), f.filePath(), textRange);
      })
      .toList();

    return new AnalyzeFileListToolResponse(findings, results.findings().size());
  }

}
