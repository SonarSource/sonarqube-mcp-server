/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class LanguageUtils {

  public static final Map<String, Set<Language>> SUPPORTED_LANGUAGES_BY_PLUGIN_KEY = new HashMap<>();

  /**
   * File-suffix keys that map to exactly one supported {@link SonarLanguage}.
   * Built from {@link SonarLanguage#getDefaultFileSuffixes()} (e.g. {@code tsx} for TS,
   * {@code ipynb} for IPYTHON); keys shared by several languages are excluded so language
   * input can be resolved unambiguously in {@link #getSonarLanguageFromInput(String)}.
   */
  private static final Map<String, SonarLanguage> UNAMBIGUOUS_SUFFIX_KEY_TO_LANGUAGE;

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
    UNAMBIGUOUS_SUFFIX_KEY_TO_LANGUAGE = buildUnambiguousSuffixKeyIndex();
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
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  public static String[] getValidLanguageNames() {
    return Stream.concat(
        SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.values().stream()
          .flatMap(Set::stream)
          .map(Language::name)
          .map(name -> name.toLowerCase(Locale.ROOT)),
        UNAMBIGUOUS_SUFFIX_KEY_TO_LANGUAGE.keySet().stream())
      .distinct()
      .sorted()
      .toArray(String[]::new);
  }

  @Nullable
  public static SonarLanguage getSonarLanguageFromInput(@Nullable String languageInput) {
    if (languageInput == null) {
      return null;
    }

    var languageKey = normalizeLanguageKey(languageInput);

    for (var sonarLanguage : getSupportedSonarLanguages()) {
      if (sonarLanguage.name().equalsIgnoreCase(languageInput)) {
        return sonarLanguage;
      }
    }

    return UNAMBIGUOUS_SUFFIX_KEY_TO_LANGUAGE.get(languageKey);
  }

  private static Map<String, SonarLanguage> buildUnambiguousSuffixKeyIndex() {
    var keyCounts = new HashMap<String, Integer>();
    for (var sonarLanguage : getSupportedSonarLanguages()) {
      fileSuffixLanguageKeys(sonarLanguage).forEach(key -> keyCounts.merge(key, 1, Integer::sum));
    }

    var index = new HashMap<String, SonarLanguage>();
    for (var sonarLanguage : getSupportedSonarLanguages()) {
      fileSuffixLanguageKeys(sonarLanguage)
        .filter(key -> keyCounts.get(key) == 1)
        .forEach(key -> index.put(key, sonarLanguage));
    }
    return Map.copyOf(index);
  }

  /**
   * Resolves the file extension for snippet analysis temp files using {@link SonarLanguage#getDefaultFileSuffixes()}.
   */
  public static String resolveAnalysisFileExtension(@Nullable String languageInput, SonarLanguage sonarLanguage) {
    String extension;
    if (languageInput != null) {
      var suffix = findFileSuffixForLanguageKey(sonarLanguage, normalizeLanguageKey(languageInput));
      extension = suffix != null ? suffix : getPrimaryFileSuffix(sonarLanguage);
    } else {
      extension = getPrimaryFileSuffix(sonarLanguage);
    }
    return normalizeFileExtension(extension);
  }

  /**
   * Language keys derived from {@link SonarLanguage#getDefaultFileSuffixes()} (e.g. {@code ipynb}, {@code tsx}).
   */
  private static Stream<String> fileSuffixLanguageKeys(SonarLanguage sonarLanguage) {
    return Arrays.stream(sonarLanguage.getDefaultFileSuffixes())
      .map(LanguageUtils::languageKeyFromFileSuffix);
  }

  @Nullable
  private static String findFileSuffixForLanguageKey(SonarLanguage sonarLanguage, String languageKey) {
    for (var suffix : sonarLanguage.getDefaultFileSuffixes()) {
      if (languageKeyFromFileSuffix(suffix).equals(languageKey)) {
        return suffix;
      }
    }
    return null;
  }

  private static String getPrimaryFileSuffix(SonarLanguage sonarLanguage) {
    var suffixes = sonarLanguage.getDefaultFileSuffixes();
    if (suffixes.length > 0 && !suffixes[0].isBlank()) {
      return suffixes[0];
    }
    return ".txt";
  }

  private static String languageKeyFromFileSuffix(String fileSuffix) {
    return fileSuffix.startsWith(".")
      ? fileSuffix.substring(1).toLowerCase(Locale.ROOT)
      : fileSuffix.toLowerCase(Locale.ROOT);
  }

  private static String normalizeFileExtension(String extension) {
    if (extension.isBlank()) {
      return ".txt";
    }
    return extension.startsWith(".") ? extension : ("." + extension);
  }

  private static String normalizeLanguageKey(String languageInput) {
    return languageInput.toLowerCase(Locale.ROOT);
  }

  @Nullable
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
