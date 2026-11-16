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
package org.sonarsource.sonarqube.mcp.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCategoryMatchingTest {

  @ParameterizedTest
  @CsvSource({
    "ANALYSIS,ANALYSIS,true", "ANALYSIS,IDE,true", "ANALYSIS,VORTEX,true", "ANALYSIS,CAG,false",
    "IDE,ANALYSIS,false", "IDE,IDE,true", "IDE,VORTEX,false", "IDE,CAG,false",
    "VORTEX,ANALYSIS,false", "VORTEX,IDE,false", "VORTEX,VORTEX,true", "VORTEX,CAG,false",
    "CAG,ANALYSIS,false", "CAG,IDE,false", "CAG,VORTEX,true", "CAG,CAG,false",
    "ISSUES,ISSUES,true", "ISSUES,VORTEX,false"
  })
  void it_should_map_selectors_to_canonical_categories(ToolCategory selector, ToolCategory category, boolean expected) {
    assertThat(selector.includes(category)).isEqualTo(expected);
  }
}
