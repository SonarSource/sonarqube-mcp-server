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
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.getSonarLanguageFromInput;
import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.mapSonarLanguageToLanguage;

public class AnalysisTool extends Tool {

  public static final String TOOL_NAME = "analyze_code_snippet";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String SNIPPET_PROPERTY = "codeSnippet";
  public static final String LANGUAGE_PROPERTY = "language";

  private final BackendService backendService;
  private final ServerApiProvider serverApiProvider;

  public AnalysisTool(BackendService backendService, ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(AnalysisToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Code File Analysis")
      .setDescription("Analyze a file or code snippet with SonarQube analyzers to identify code quality and security issues. " +
        "Specify the language of the snippet to improve analysis accuracy.")
      .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The SonarQube project key")
      .addRequiredStringProperty(SNIPPET_PROPERTY, "Code snippet or full file content")
      .addStringProperty(LANGUAGE_PROPERTY, "Language of the code snippet")
      .setReadOnlyHint()
      .build(),
      ToolCategory.ANALYSIS);
    this.backendService = backendService;
    this.serverApiProvider = serverApiProvider;
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
      var toolResponse = buildStructuredContent(response);
      return Tool.Result.success(toolResponse);
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
    serverApiProvider.get().qualityProfilesApi().getQualityProfiles(projectKey).profiles()
      .forEach(profile -> {
        var count = 0;
        var page = 1;
        SearchResponse searchResponse;
        do {
          searchResponse = serverApiProvider.get().rulesApi().search(profile.key(), page);
          page++;
          count += searchResponse.ps();
          searchResponse.actives().forEach((ruleKey, actives) -> activeRules.put(ruleKey,
            new StandaloneRuleConfigDto(true, actives.getFirst().params().stream().collect(toMap(SearchResponse.RuleParameter::key, SearchResponse.RuleParameter::value)))));
        } while (count < searchResponse.total());
      });
    backendService.updateRulesConfiguration(activeRules);
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

  public AnalysisToolResponse buildStructuredContent(AnalyzeFilesResponse response) {
    var issues = response.getRawIssues().stream()
      .map(issue -> {
        AnalysisToolResponse.TextRange textRange = null;
        if (issue.getTextRange() != null) {
          textRange = new AnalysisToolResponse.TextRange(
            issue.getTextRange().getStartLine(),
            issue.getTextRange().getEndLine()
          );
        }
        
        return new AnalysisToolResponse.Issue(
          issue.getRuleKey(),
          issue.getPrimaryMessage(),
          issue.getSeverity().toString(),
          issue.getCleanCodeAttribute().name(),
          issue.getImpacts().toString(),
          !issue.getQuickFixes().isEmpty(),
          textRange
        );
      })
      .toList();

    return new AnalysisToolResponse(issues, response.getRawIssues().size());
  }

}
