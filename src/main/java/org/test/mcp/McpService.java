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
package org.test.mcp;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.test.analysis.AnalysisUtils;
import org.test.sloop.BackendService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import static org.test.analysis.AnalysisUtils.buildResponseFromAnalysisResults;
import static org.test.analysis.AnalysisUtils.getSonarLanguageFromInput;
import static org.test.analysis.AnalysisUtils.mapSonarLanguageToLanguage;
import static org.test.analysis.AnalysisUtils.removeTmpFileForAnalysis;

@Service
public class McpService {

  private static final String PROJECT_ID = "sonar-mcp-server";
  private final BackendService backendService;

  public McpService(BackendService backendService) {
    this.backendService = backendService;
    backendService.projectOpened(PROJECT_ID);
  }

  @Tool(description = "Get rule details (name, language, content) in standalone mode")
  public String getStandaloneRuleDetails(@ToolParam(description = "The sonar rule key in format repositoryId:ruleId, for example squid:S109") String ruleKey) throws ExecutionException, InterruptedException {
    var ruleDetails = backendService.getStandaloneRuleDetails(new GetStandaloneRuleDescriptionParams(ruleKey)).get();
    var stringBuilder = new StringBuilder();
    stringBuilder.append("Rule name: ").append(ruleDetails.getRuleDefinition().getName());
    stringBuilder.append("\n");
    stringBuilder.append("Language: ").append(ruleDetails.getRuleDefinition().getLanguage().toString());
    stringBuilder.append("\n");
    if (ruleDetails.getDescription().isLeft()) {
      stringBuilder.append("Rule content (HTML format): ").append(ruleDetails.getDescription().getLeft().getHtmlContent());
    } else {
      stringBuilder.append("Rule introduction content (HTML format): ").append(ruleDetails.getDescription().getRight().getIntroductionHtmlContent());
    }
    return stringBuilder.toString();
  }

  @Tool(description = """
    Find Sonar issues in a code snippet. If possible, the language of the code snippet should be known, but it can be null otherwise.
    """)
  public String findSonarIssuesInCodeSnippet(String codeSnippet, @Nullable String language) {
    System.out.println("Starting analysis of the code snippet");
    var sonarLanguage = getSonarLanguageFromInput(language);
    if (sonarLanguage == null) {
      System.out.println("Language determined as SECRETS by default");
      sonarLanguage = SonarLanguage.SECRETS;
    }

    var analysisId = UUID.randomUUID();
    Path tmpFile = null;
    try {
      tmpFile = AnalysisUtils.createTemporaryFileForLanguage(analysisId.toString(), backendService.getWorkDir(), codeSnippet,
        sonarLanguage);
      backendService.addFile(new ClientFileDto(tmpFile.toUri(), tmpFile, PROJECT_ID, false, Charset.defaultCharset().toString(), tmpFile,
        codeSnippet, mapSonarLanguageToLanguage(sonarLanguage), true));
      var startTime = System.currentTimeMillis();
      var response = backendService.analyzeFilesAndTrack(PROJECT_ID, analysisId, List.of(tmpFile.toUri()), startTime).get(20,
        TimeUnit.SECONDS);
      return buildResponseFromAnalysisResults(response);
    } catch (Exception e) {
      return "Analysis failed: " + e.getMessage();
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

}
