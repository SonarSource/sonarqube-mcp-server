/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.sonar.mcp.slcore.BackendService;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.springframework.stereotype.Service;

import static org.sonar.mcp.analysis.LanguageUtils.getSonarLanguageFromInput;
import static org.sonar.mcp.analysis.LanguageUtils.mapSonarLanguageToLanguage;

@Service
public class FindIssuesTool {

  private static final String PROJECT_ID = "sonar-mcp-server";
  private final BackendService backendService;

  public FindIssuesTool(BackendService backendService) {
    this.backendService = backendService;
    backendService.projectOpened(PROJECT_ID);
  }

  public McpSchema.Tool definition() {
    return new McpSchema.Tool(
      "findSonarIssuesInCodeSnippet",
      "Find Sonar issues in a code snippet. If possible, the language of the code snippet should be known, but it can be null otherwise.",
      new McpSchema.JsonSchema(
        "object",
        Map.of(
          "codeSnippet", Map.of("type", "string", "description", ""),
          "language", Map.of("type", "string", "description", "")
        ),
        List.of("codeSnippet", "language"),
        false
      )
    );
  }

  public McpServerFeatures.SyncToolSpecification spec() {
    return new McpServerFeatures.SyncToolSpecification(
      definition(),
      (McpSyncServerExchange exchange, Map<String, Object> argMap) -> findSonarIssuesInCodeSnippet(argMap)
    );
  }

  private McpSchema.CallToolResult findSonarIssuesInCodeSnippet(Map<String, Object> args) {
    var text = new StringBuilder();

    var codeSnippet = ((String) args.get("codeSnippet"));
    var language = ((String) args.get("language"));

    var sonarLanguage = getSonarLanguageFromInput(language);
    if (sonarLanguage == null) {
      sonarLanguage = SonarLanguage.SECRETS;
    }

    var analysisId = UUID.randomUUID();
    Path tmpFile = null;
    try {
      tmpFile = createTemporaryFileForLanguage(analysisId.toString(), backendService.getWorkDir(), codeSnippet,
        sonarLanguage);
      backendService.addFile(new ClientFileDto(tmpFile.toUri(), tmpFile, PROJECT_ID, false, Charset.defaultCharset().toString(), tmpFile,
        codeSnippet, mapSonarLanguageToLanguage(sonarLanguage), true));
      var startTime = System.currentTimeMillis();
      var response = backendService.analyzeFilesAndTrack(PROJECT_ID, analysisId, List.of(tmpFile.toUri()), startTime).get(20,
        TimeUnit.SECONDS);
      text.append(buildResponseFromAnalysisResults(response));
    } catch (Exception e) {
      text.append("Analysis failed: ").append(e.getMessage());
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

    return McpSchema.CallToolResult.builder()
      .addTextContent(text.toString())
      .build();
  }

  private static String buildResponseFromAnalysisResults(AnalyzeFilesResponse response) {
    var stringBuilder = new StringBuilder();

    if (!response.getFailedAnalysisFiles().isEmpty()) {
      stringBuilder.append("Failed to analyze the following files: ");
      response.getFailedAnalysisFiles().forEach(file -> stringBuilder.append(file.toString()).append(", "));
    }

    if (response.getRawIssues().isEmpty()) {
      stringBuilder.append("No Sonar issues found in the files.");
    } else {
      stringBuilder.append("Found ").append(response.getRawIssues().size()).append(" Sonar issues in the file");

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

    return stringBuilder.toString();
  }

  private static Path createTemporaryFileForLanguage(String analysisId, Path workDir, String fileContent, SonarLanguage language) throws IOException {
    var extension = language.getDefaultFileSuffixes()[0];
    if (extension.isBlank()) {
      extension = "txt";
    }
    var tempFile = workDir.resolve("analysis-" + analysisId + "." + extension);
    Files.writeString(tempFile, fileContent);
    return tempFile;
  }

  private static void removeTmpFileForAnalysis(Path tempFile) throws IOException {
    Files.deleteIfExists(tempFile);
  }

}
