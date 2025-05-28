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
package org.sonarsource.sonarqube.mcp.analysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class LanguageUtils {

  private static final Map<String, Set<Language>> supportedLanguagesPerAnalyzers = createSupportedLanguagesPerAnalyzers();

  private static Map<String, Set<Language>> createSupportedLanguagesPerAnalyzers() {
    var analyzers = new HashMap<String, Set<Language>>();
    analyzers.put("sonar-kotlin-plugin", Set.of(Language.KOTLIN));
    analyzers.put("sonar-java-plugin", Set.of(Language.JAVA));
    analyzers.put("sonar-iac-plugin", Set.of(Language.CLOUDFORMATION, Language.KUBERNETES, Language.TERRAFORM,
      Language.AZURERESOURCEMANAGER, Language.ANSIBLE, Language.DOCKER));
    analyzers.put("sonar-python-plugin", Set.of(Language.PYTHON, Language.IPYTHON));
    analyzers.put("sonar-ruby-plugin", Set.of(Language.RUBY));
    analyzers.put("sonar-java-symbolic-execution-plugin", Collections.emptySet());
    analyzers.put("sonar-go-plugin", Set.of(Language.GO));
    analyzers.put("sonar-javascript-plugin", Set.of(Language.JS, Language.TS, Language.JSP));
    analyzers.put("sonar-text-plugin", Set.of(Language.SECRETS));
    analyzers.put("sonar-php-plugin", Set.of(Language.PHP));
    analyzers.put("sonar-xml-plugin", Set.of(Language.XML));
    analyzers.put("sonar-html-plugin", Set.of(Language.HTML, Language.CSS));
    return analyzers;
  }

  public static Map<String, Set<Language>> getSupportedLanguagesPerAnalyzers() {
    return supportedLanguagesPerAnalyzers;
  }

  public static Set<SonarLanguage> getSupportedSonarLanguages() {
    return supportedLanguagesPerAnalyzers.values().stream()
      .flatMap(Set::stream)
      .map(language -> {
        for (var sonarLanguage : SonarLanguage.values()) {
          if (sonarLanguage.name().equalsIgnoreCase(language.name())) {
            return sonarLanguage;
          }
        }
        return null;
      })
      .filter(java.util.Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @CheckForNull
  public static SonarLanguage getSonarLanguageFromInput(@Nullable String languageInput) {
    for (var sonarLanguage : getSupportedSonarLanguages()) {
      if (sonarLanguage.getPluginKey().equalsIgnoreCase(languageInput)) {
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

  private LanguageUtils() {
    // Utility class
  }

}
