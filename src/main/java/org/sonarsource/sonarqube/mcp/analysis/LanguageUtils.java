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

  public static final Map<String, Set<Language>> SUPPORTED_LANGUAGES_BY_PLUGIN_KEY = new HashMap<>();

  static {
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("kotlin", Set.of(Language.KOTLIN));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("java", Set.of(Language.JAVA));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("iac",
      Set.of(Language.CLOUDFORMATION, Language.KUBERNETES, Language.TERRAFORM, Language.AZURERESOURCEMANAGER, Language.ANSIBLE, Language.DOCKER));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("python", Set.of(Language.PYTHON, Language.IPYTHON));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("ruby", Set.of(Language.RUBY));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("javasymbolicexecution", Collections.emptySet());
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("go", Set.of(Language.GO));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("javascript", Set.of(Language.JS, Language.TS, Language.JSP));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("text", Set.of(Language.SECRETS));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("textenterprise", Set.of(Language.SECRETS));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("php", Set.of(Language.PHP));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("xml", Set.of(Language.XML));
    SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.put("web", Set.of(Language.HTML, Language.CSS));
  }

  public static Set<SonarLanguage> getSupportedSonarLanguages() {
    return SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.values().stream()
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
