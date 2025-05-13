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
package org.sonar.mcp.analysis;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class LanguageUtils {

  private static final List<SonarLanguage> supportedSonarLanguages = List.of(
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

  public static Set<Language> getSupportedSonarLanguages() {
    return supportedSonarLanguages.stream()
      .map(LanguageUtils::mapSonarLanguageToLanguage)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  @CheckForNull
  public static SonarLanguage getSonarLanguageFromInput(@Nullable String languageInput) {
    for (var sonarLanguage : supportedSonarLanguages) {
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

  private LanguageUtils() {
    // Utility class
  }

}
