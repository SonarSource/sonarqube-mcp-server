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
import java.util.HashMap;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageUtilsTests {

  @Test
  void should_return_sonar_language_for_valid_string_input() {
    var result = LanguageUtils.getSonarLanguageFromInput("java");

    assertThat(result).isEqualTo(SonarLanguage.JAVA);
  }

  @Test
  void should_return_null_for_invalid_string_input() {
    var result = LanguageUtils.getSonarLanguageFromInput("invalid");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_for_null_string_input() {
    assertThat(LanguageUtils.getSonarLanguageFromInput(null)).isNull();
  }

  @Test
  void should_map_sonar_language_to_language_when_valid() {
    var result = LanguageUtils.mapSonarLanguageToLanguage(SonarLanguage.JAVA);

    assertThat(result).isEqualTo(Language.JAVA);
  }

  @ParameterizedTest
  @CsvSource({
    "tsx, TS",
    "TSX, TS",
    "jsx, JS",
    "JSX, JS"
  })
  void should_resolve_jsx_language_aliases(String input, SonarLanguage expected) {
    assertThat(LanguageUtils.getSonarLanguageFromInput(input)).isEqualTo(expected);
  }

  @Test
  void should_include_only_unambiguous_suffix_keys_in_valid_language_names() {
    var names = Arrays.asList(LanguageUtils.getValidLanguageNames());
    var keyCounts = new HashMap<String, Integer>();

    for (var sonarLanguage : LanguageUtils.getSupportedSonarLanguages()) {
      for (var suffix : sonarLanguage.getDefaultFileSuffixes()) {
        var key = suffix.startsWith(".") ? suffix.substring(1).toLowerCase(Locale.ROOT) : suffix.toLowerCase(Locale.ROOT);
        keyCounts.merge(key, 1, Integer::sum);
        if (keyCounts.get(key) == 1) {
          assertThat(names).as("missing unambiguous suffix key %s for %s", key, sonarLanguage).contains(key);
        } else {
          assertThat(names).as("ambiguous suffix key %s must not be exposed", key).doesNotContain(key);
        }
      }
    }
  }

  @Test
  void should_resolve_unambiguous_suffix_keys_only() {
    var keyCounts = new HashMap<String, Integer>();
    for (var sonarLanguage : LanguageUtils.getSupportedSonarLanguages()) {
      for (var suffix : sonarLanguage.getDefaultFileSuffixes()) {
        var key = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        keyCounts.merge(key, 1, Integer::sum);
      }
    }

    for (var expected : LanguageUtils.getSupportedSonarLanguages()) {
      for (var suffix : expected.getDefaultFileSuffixes()) {
        var key = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        var resolved = LanguageUtils.getSonarLanguageFromInput(key);

        if (keyCounts.get(key) == 1) {
          assertThat(resolved).as("suffix key %s", key).isEqualTo(expected);
          var expectedExtension = suffix.startsWith(".") ? suffix : "." + suffix;
          assertThat(LanguageUtils.resolveAnalysisFileExtension(key, resolved)).isEqualTo(expectedExtension);
        } else {
          assertThat(resolved).as("ambiguous suffix key %s", key).isNull();
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "tsx, TS, .tsx",
    "jsx, JS, .jsx",
    "ts, TS, .ts",
    "js, JS, .js",
    "ipynb, IPYTHON, .ipynb",
    "ipython, IPYTHON, .ipynb",
    "java, JAVA, .java",
    "jav, JAVA, .jav",
    "kt, KOTLIN, .kt",
    "kts, KOTLIN, .kts",
    "kotlin, KOTLIN, .kt",
    "php3, PHP, .php3",
    "jspf, JSP, .jspf"
  })
  void should_resolve_language_and_extension(String languageInput, SonarLanguage expectedLanguage, String expectedExtension) {
    var resolved = LanguageUtils.getSonarLanguageFromInput(languageInput);

    assertThat(resolved).isEqualTo(expectedLanguage);
    assertThat(LanguageUtils.resolveAnalysisFileExtension(languageInput, resolved)).isEqualTo(expectedExtension);
  }

}
