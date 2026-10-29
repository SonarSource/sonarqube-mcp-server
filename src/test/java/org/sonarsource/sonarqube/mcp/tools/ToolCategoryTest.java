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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCategoryTest {

  @ParameterizedTest
  @EnumSource(TransportMode.class)
  void it_should_resolve_transport_defaults_for_unset_or_blank_selection(TransportMode transport) {
    var defaults = ToolCategory.defaultEnabled(transport);
    for (var selection : new String[] {null, "", " \t "}) {
      assertThat(ToolCategory.parseCategories(selection, transport)).isEqualTo(defaults);
    }
    assertThat(defaults).doesNotContain(ToolCategory.ARCHITECTURE, ToolCategory.VORTEX, ToolCategory.SYSTEM);
    if (transport == TransportMode.STDIO) {
      assertThat(defaults).contains(ToolCategory.AGENTIC_READINESS);
    } else {
      assertThat(defaults).containsExactlyInAnyOrderElementsOf(
        ToolCategory.defaultEnabled(TransportMode.STDIO).stream().filter(category -> category != ToolCategory.AGENTIC_READINESS).toList());
    }
  }

  @ParameterizedTest
  @EnumSource(TransportMode.class)
  void it_should_preserve_explicit_selection_for_each_transport(TransportMode transport) {
    assertThat(ToolCategory.parseCategories(" ISSUES,agentic-readiness,invalid,architecture ", transport))
      .containsExactlyInAnyOrder(ToolCategory.ISSUES, ToolCategory.AGENTIC_READINESS, ToolCategory.ARCHITECTURE);
  }

  @Test
  void should_have_correct_keys() {
    assertThat(ToolCategory.ANALYSIS.getKey()).isEqualTo("analysis");
    assertThat(ToolCategory.IDE.getKey()).isEqualTo("ide");
    assertThat(ToolCategory.COVERAGE.getKey()).isEqualTo("coverage");
    assertThat(ToolCategory.ISSUES.getKey()).isEqualTo("issues");
    assertThat(ToolCategory.PROJECTS.getKey()).isEqualTo("projects");
    assertThat(ToolCategory.QUALITY_GATES.getKey()).isEqualTo("quality-gates");
    assertThat(ToolCategory.RULES.getKey()).isEqualTo("rules");
    assertThat(ToolCategory.SOURCES.getKey()).isEqualTo("sources");
    assertThat(ToolCategory.MEASURES.getKey()).isEqualTo("measures");
    assertThat(ToolCategory.LANGUAGES.getKey()).isEqualTo("languages");
    assertThat(ToolCategory.PORTFOLIOS.getKey()).isEqualTo("portfolios");
    assertThat(ToolCategory.SYSTEM.getKey()).isEqualTo("system");
    assertThat(ToolCategory.WEBHOOKS.getKey()).isEqualTo("webhooks");
    assertThat(ToolCategory.DEPENDENCY_RISKS.getKey()).isEqualTo("dependency-risks");
    assertThat(ToolCategory.CAG.getKey()).isEqualTo("cag");
    assertThat(ToolCategory.VORTEX.getKey()).isEqualTo("vortex");
    assertThat(ToolCategory.ARCHITECTURE.getKey()).isEqualTo("architecture");
  }

  @Test
  void should_parse_category_from_key() {
    assertThat(ToolCategory.fromKey("analysis")).isEqualTo(ToolCategory.ANALYSIS);
    assertThat(ToolCategory.fromKey("issues")).isEqualTo(ToolCategory.ISSUES);
    assertThat(ToolCategory.fromKey("quality-gates")).isEqualTo(ToolCategory.QUALITY_GATES);
    assertThat(ToolCategory.fromKey("dependency-risks")).isEqualTo(ToolCategory.DEPENDENCY_RISKS);
  }

  @Test
  void should_parse_category_case_insensitively() {
    assertThat(ToolCategory.fromKey("ANALYSIS")).isEqualTo(ToolCategory.ANALYSIS);
    assertThat(ToolCategory.fromKey("Analysis")).isEqualTo(ToolCategory.ANALYSIS);
    assertThat(ToolCategory.fromKey("QUALITY-GATES")).isEqualTo(ToolCategory.QUALITY_GATES);
  }

  @Test
  void should_trim_whitespace_when_parsing() {
    assertThat(ToolCategory.fromKey("  analysis  ")).isEqualTo(ToolCategory.ANALYSIS);
    assertThat(ToolCategory.fromKey("\tquality-gates\n")).isEqualTo(ToolCategory.QUALITY_GATES);
  }

  @Test
  void should_return_null_for_invalid_key() {
    assertThat(ToolCategory.fromKey("invalid")).isNull();
    assertThat(ToolCategory.fromKey("")).isNull();
    assertThat(ToolCategory.fromKey(null)).isNull();
    assertThat(ToolCategory.fromKey("   ")).isNull();
  }

  @Test
  void should_parse_multiple_categories_from_comma_separated_string() {
    var categories = ToolCategory.parseCategories("analysis,issues,rules");
    
    assertThat(categories).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.ISSUES,
      ToolCategory.RULES
    );
  }

  @Test
  void should_parse_categories_with_whitespace() {
    var categories = ToolCategory.parseCategories("  analysis  ,  issues  ,  rules  ");
    
    assertThat(categories).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.ISSUES,
      ToolCategory.RULES
    );
  }

  @Test
  void should_ignore_invalid_categories_when_parsing() {
    var categories = ToolCategory.parseCategories("analysis,invalid,issues,badkey");
    
    assertThat(categories).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.ISSUES
    );
  }

  @Test
  void should_return_default_enabled_categories_for_null_or_empty_string() {
    var defaultCategories = ToolCategory.defaultEnabled();

    assertThat(ToolCategory.parseCategories(null)).isEqualTo(defaultCategories);
    assertThat(ToolCategory.parseCategories("")).isEqualTo(defaultCategories);
    assertThat(ToolCategory.parseCategories("   ")).isEqualTo(defaultCategories);
  }

  @Test
  void should_return_default_enabled_categories() {
    var defaultCategories = ToolCategory.defaultEnabled();

    assertThat(defaultCategories).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.IDE,
      ToolCategory.ISSUES,
      ToolCategory.PROJECTS,
      ToolCategory.QUALITY_GATES,
      ToolCategory.RULES,
      ToolCategory.DUPLICATIONS,
      ToolCategory.MEASURES,
      ToolCategory.SECURITY_HOTSPOTS,
      ToolCategory.DEPENDENCY_RISKS,
      ToolCategory.COVERAGE,
      ToolCategory.CAG,
      ToolCategory.AGENTIC_READINESS
    );
  }

  @Test
  void should_return_all_categories() {
    var allCategories = ToolCategory.all();
    
    assertThat(allCategories).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.IDE,
      ToolCategory.COVERAGE,
      ToolCategory.ISSUES,
      ToolCategory.PROJECTS,
      ToolCategory.QUALITY_GATES,
      ToolCategory.RULES,
      ToolCategory.SOURCES,
      ToolCategory.DUPLICATIONS,
      ToolCategory.MEASURES,
      ToolCategory.LANGUAGES,
      ToolCategory.PORTFOLIOS,
      ToolCategory.SYSTEM,
      ToolCategory.WEBHOOKS,
      ToolCategory.DEPENDENCY_RISKS,
      ToolCategory.SECURITY_HOTSPOTS,
      ToolCategory.CAG,
      ToolCategory.AGENTIC_READINESS,
      ToolCategory.VORTEX,
      ToolCategory.ARCHITECTURE
    );
  }

  @Test
  void should_have_exactly_20_categories() {
    assertThat(ToolCategory.values()).hasSize(20);
  }
}

