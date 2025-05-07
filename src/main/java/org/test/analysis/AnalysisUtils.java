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
package org.test.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class AnalysisUtils {

  public static final List<SonarLanguage> languageToExtensionMap = List.of(
    SonarLanguage.JAVA,
    SonarLanguage.PHP,
    SonarLanguage.CSS,
    SonarLanguage.HTML,
    SonarLanguage.IPYTHON,
    SonarLanguage.RUBY,
    SonarLanguage.SECRETS,
    SonarLanguage.TSQL,
    SonarLanguage.JS,
    SonarLanguage.TS,
    SonarLanguage.JSP,
    SonarLanguage.XML,
    SonarLanguage.YAML,
    SonarLanguage.JSON,
    SonarLanguage.GO,
    SonarLanguage.CLOUDFORMATION,
    SonarLanguage.DOCKER,
    SonarLanguage.KUBERNETES,
    SonarLanguage.TERRAFORM,
    SonarLanguage.AZURERESOURCEMANAGER,
    SonarLanguage.ANSIBLE
  );

  public static Set<Language> getSupportedLanguages() {
    return languageToExtensionMap.stream()
      .map(AnalysisUtils::mapSonarLanguageToLanguage)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public static String buildResponseFromAnalysisResults(AnalyzeFilesResponse response) {
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

  @CheckForNull
  public static SonarLanguage getSonarLanguageFromInput(@Nullable String languageInput) {
    for (var sonarLanguage : languageToExtensionMap) {
      if (sonarLanguage.getSonarLanguageKey().equalsIgnoreCase(languageInput)) {
        return sonarLanguage;
      }
    }
    return null;
  }

  @CheckForNull
  public static Language mapSonarLanguageToLanguage(SonarLanguage sonarLanguage) {
    for (var language : Language.values()) {
      if (language.name().equalsIgnoreCase(sonarLanguage.name())) {
        return language;
      }
    }
    return null;
  }

  public static Path createTemporaryFileForLanguage(String analysisId, Path workDir, String fileContent, SonarLanguage language) throws IOException {
    var extension = language.getDefaultFileSuffixes()[0];
    if (extension.isBlank()) {
      extension = "txt";
    }
    var tempFile = workDir.resolve("analysis-" + analysisId + "." + extension);
    Files.writeString(tempFile, fileContent);
    return tempFile;
  }

  public static void removeTmpFileForAnalysis(Path tempFile) throws IOException {
    Files.deleteIfExists(tempFile);
  }

  private AnalysisUtils() {
    // Utility class
  }

}
