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

}
