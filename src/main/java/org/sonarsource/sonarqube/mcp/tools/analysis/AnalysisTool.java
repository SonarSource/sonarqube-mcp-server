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

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.getSonarLanguageFromInput;
import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.mapSonarLanguageToLanguage;

public class AnalysisTool extends Tool {

  public static final String TOOL_NAME = "analyze_code_snippet";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String SNIPPET_PROPERTY = "codeSnippet";
  public static final String LANGUAGE_PROPERTY = "language";

  private final BackendService backendService;
  private final ServerApi serverApi;

  public AnalysisTool(BackendService backendService, ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Analyze a file or code snippet with SonarQube analyzers to identify code quality and security issues. " +
        "Specify the language of the snippet to improve analysis accuracy.")
      .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The SonarQube project key")
      .addRequiredStringProperty(SNIPPET_PROPERTY, "Code snippet or full file content")
      .addStringProperty(LANGUAGE_PROPERTY, "Language of the code snippet")
      .build());
    this.backendService = backendService;
    this.serverApi = serverApi;
  }

  @Override
  public Result execute(Arguments arguments) {
    var projectKey = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    var codeSnippet = arguments.getStringOrThrow(SNIPPET_PROPERTY);
    var language = arguments.getOptionalString(LANGUAGE_PROPERTY);

    var sonarLanguage = getSonarLanguageFromInput(language);
    if (sonarLanguage == null) {
      sonarLanguage = SonarLanguage.SECRETS;
    }

    applyRulesFromProject(projectKey);

    var analysisId = UUID.randomUUID();
    Path tmpFile = null;
    try {
      tmpFile = createTemporaryFileForLanguage(analysisId.toString(), backendService.getWorkDir(), codeSnippet,
        sonarLanguage);
      var clientFileDto = backendService.toClientFileDto(tmpFile, codeSnippet, mapSonarLanguageToLanguage(sonarLanguage));
      backendService.addFile(clientFileDto);
      var startTime = System.currentTimeMillis();
      var response = backendService.analyzeFilesAndTrack(analysisId, List.of(tmpFile.toUri()), startTime).get(30,
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

  private void applyRulesFromProject(@Nullable String projectKey) {
    var activeRules = new HashMap<String, StandaloneRuleConfigDto>();
    serverApi.qualityProfilesApi().getQualityProfiles(projectKey).profiles()
      .forEach(profile -> {
        var count = 0;
        var page = 1;
        SearchResponse searchResponse;
        do {
          searchResponse = serverApi.rulesApi().search(profile.key(), page);
          page++;
          count += searchResponse.ps();
          searchResponse.actives().forEach((ruleKey, actives) -> activeRules.put(ruleKey,
            new StandaloneRuleConfigDto(true, actives.getFirst().params().stream().collect(toMap(SearchResponse.RuleParameter::key, SearchResponse.RuleParameter::value)))));
        } while (count < searchResponse.total());
      });
    backendService.updateRulesConfiguration(activeRules);
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

    stringBuilder.append("\nDisclaimer: Analysis results might not be fully accurate as the code snippet is not part of a complete project context." +
      " Use SonarQube for IDE for better results, or setup a full project analysis in SonarQube Server or Cloud.");

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
