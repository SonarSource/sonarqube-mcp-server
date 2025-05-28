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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.getSonarLanguageFromInput;
import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.mapSonarLanguageToLanguage;

public class AnalysisTool extends Tool {

  public static final String TOOL_NAME = "analyze_code_snippet_with_sonarqube";
  public static final String SNIPPET_PROPERTY = "codeSnippet";
  public static final String LANGUAGE_PROPERTY = "language";

  private final BackendService backendService;

  public AnalysisTool(BackendService backendService) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Analyze a code snippet with Sonar analyzers to find Sonar issues in it.")
      .addRequiredStringProperty(SNIPPET_PROPERTY, "Code snippet or full file content")
      .addStringProperty(LANGUAGE_PROPERTY, "Language of the code snippet")
      .build());
    this.backendService = backendService;
  }

  @Override
  public Result execute(Arguments arguments) {
    var codeSnippet = arguments.getStringOrThrow(SNIPPET_PROPERTY);
    var language = arguments.getOptionalString(LANGUAGE_PROPERTY);

    var sonarLanguage = getSonarLanguageFromInput(language);
    if (sonarLanguage == null) {
      sonarLanguage = SonarLanguage.SECRETS;
    }

    var analysisId = UUID.randomUUID();
    Path tmpFile = null;
    try {
      tmpFile = createTemporaryFileForLanguage(analysisId.toString(), backendService.getWorkDir(), codeSnippet,
        sonarLanguage);
      var clientFileDto = backendService.toClientFileDto(tmpFile, codeSnippet, mapSonarLanguageToLanguage(sonarLanguage));
      backendService.addFile(clientFileDto);
      var startTime = System.currentTimeMillis();
      var response = backendService.analyzeFilesAndTrack(analysisId, List.of(tmpFile.toUri()), startTime).get(20,
        TimeUnit.SECONDS);
      return Tool.Result.success(buildResponseFromAnalysisResults(response));
    } catch (IOException | ExecutionException | TimeoutException e) {
      return Tool.Result.failure("Error while analyzing the code snippet: " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Tool.Result.failure("Error while analyzing the code snippet: " + e.getMessage());
    } finally {
      if (tmpFile != null) {
        backendService.removeFile(tmpFile.toUri());
        try {
          removeTmpFileForAnalysis(tmpFile);
        } catch (IOException e) {
          // Error
        }
      }
    }
  }

  private static String buildResponseFromAnalysisResults(AnalyzeFilesResponse response) {
    var stringBuilder = new StringBuilder();

    if (!response.getFailedAnalysisFiles().isEmpty()) {
      stringBuilder.append("Failed to analyze the code snippet.");
      return stringBuilder.toString();
    }

    if (response.getRawIssues().isEmpty()) {
      stringBuilder.append("No Sonar issues found in the code snippet.");
    } else {
      stringBuilder.append("Found ").append(response.getRawIssues().size()).append(" Sonar issues in the code snippet");

      for (var issue : response.getRawIssues()) {
        stringBuilder.append("\n");
        stringBuilder.append(issue.getPrimaryMessage());
        stringBuilder.append("\n");
        stringBuilder.append("Rule key: ").append(issue.getRuleKey());
        stringBuilder.append("\n");
        stringBuilder.append("Severity: ").append(issue.getSeverity());
        stringBuilder.append("\n");
        stringBuilder.append("Clean Code attribute: ").append(issue.getCleanCodeAttribute().name());
        stringBuilder.append("\n");
        stringBuilder.append("Impacts: ").append(issue.getImpacts().toString());
        stringBuilder.append("\n");
        stringBuilder.append("Description: ").append(issue.getPrimaryMessage());
        stringBuilder.append("\n");
        stringBuilder.append("Quick fixes available: ").append(issue.getQuickFixes().isEmpty() ? "No" : "Yes");

        var textRange = issue.getTextRange();
        if (textRange != null) {
          stringBuilder.append("\n");
          stringBuilder.append("Starting on line: ").append(textRange.getStartLine());
        }
      }
    }

    return stringBuilder.toString().trim();
  }

  private static Path createTemporaryFileForLanguage(String analysisId, Path workDir, String fileContent, SonarLanguage language) throws IOException {
    var defaultFileSuffixes = language.getDefaultFileSuffixes();
    var extension = defaultFileSuffixes.length > 0 ? defaultFileSuffixes[0] : "";
    if (extension.isBlank()) {
      extension = ".txt";
    }
    var tempFile = workDir.resolve("analysis-" + analysisId + extension);
    Files.writeString(tempFile, fileContent);
    return tempFile;
  }

  private static void removeTmpFileForAnalysis(Path tempFile) throws IOException {
    Files.deleteIfExists(tempFile);
  }

}
