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
import org.junit.jupiter.api.Test;
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
    var result = LanguageUtils.getSonarLanguageFromInput(null);

    assertThat(result).isNull();
  }

  @Test
  void should_map_sonar_language_to_language_when_valid() {
    var result = LanguageUtils.mapSonarLanguageToLanguage(SonarLanguage.JAVA);

    assertThat(result).isEqualTo(Language.JAVA);
  }

  // --- tsx/jsx alias resolution ---

  @Test
  void should_resolve_tsx_alias_to_ts_sonar_language() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("tsx")).isEqualTo(SonarLanguage.TS);
  }

  @Test
  void should_resolve_jsx_alias_to_js_sonar_language() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("jsx")).isEqualTo(SonarLanguage.JS);
  }

  @Test
  void should_resolve_tsx_alias_case_insensitively() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("TSX")).isEqualTo(SonarLanguage.TS);
    assertThat(LanguageUtils.getSonarLanguageFromInput("Tsx")).isEqualTo(SonarLanguage.TS);
  }

  @Test
  void should_resolve_jsx_alias_case_insensitively() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("JSX")).isEqualTo(SonarLanguage.JS);
    assertThat(LanguageUtils.getSonarLanguageFromInput("Jsx")).isEqualTo(SonarLanguage.JS);
  }

  // --- regression: existing ts/js behaviour unchanged ---

  @Test
  void should_still_resolve_ts_to_ts_sonar_language() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("ts")).isEqualTo(SonarLanguage.TS);
  }

  @Test
  void should_still_resolve_js_to_js_sonar_language() {
    assertThat(LanguageUtils.getSonarLanguageFromInput("js")).isEqualTo(SonarLanguage.JS);
  }

  // --- valid language names ---

  @Test
  void should_include_tsx_and_jsx_in_valid_language_names() {
    var names = Arrays.asList(LanguageUtils.getValidLanguageNames());

    assertThat(names).contains("tsx", "jsx");
  }

  @Test
  void should_not_duplicate_existing_languages_when_tsx_and_jsx_are_added() {
    var names = Arrays.asList(LanguageUtils.getValidLanguageNames());

    assertThat(names.stream().filter("ts"::equals).count()).isEqualTo(1);
    assertThat(names.stream().filter("js"::equals).count()).isEqualTo(1);
    assertThat(names.stream().filter("tsx"::equals).count()).isEqualTo(1);
    assertThat(names.stream().filter("jsx"::equals).count()).isEqualTo(1);
  }

  // --- file extension overrides ---

  @Test
  void should_map_tsx_to_tsx_file_extension() {
    assertThat(LanguageUtils.JSX_FILE_EXTENSIONS.get("tsx")).isEqualTo(".tsx");
  }

  @Test
  void should_map_jsx_to_jsx_file_extension() {
    assertThat(LanguageUtils.JSX_FILE_EXTENSIONS.get("jsx")).isEqualTo(".jsx");
  }

  @Test
  void should_not_override_extension_for_plain_ts() {
    assertThat(LanguageUtils.JSX_FILE_EXTENSIONS.get("ts")).isNull();
  }

  @Test
  void should_not_override_extension_for_plain_js() {
    assertThat(LanguageUtils.JSX_FILE_EXTENSIONS.get("js")).isNull();
  }

}
