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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture.Constraint;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture.Group;

import static org.assertj.core.api.Assertions.assertThat;

class IntendedRevisionTest {

  private static final IntendedArchitecture TS = new IntendedArchitecture("file", "ts",
    List.of(new Group("src", List.of("src/**"), List.of(
      new Group("api", List.of("src/api/**"), null, true, null),
      new Group("ui", List.of("src/ui/**"), null, null, null)), null, null)),
    List.of(new Constraint("src/ui", "src/api")));

  private static final IntendedArchitecture JAVA = new IntendedArchitecture("namespace", "java",
    List.of(
      new Group("Core \"é\" <x>", List.of("core:b.**", "core:B.**", "core:a.**"),
        List.of(new Group("Api", List.of("core:a.api.**"), null, true, null)), null, true),
      new Group("Web", List.of("web:**"), null, null, null)),
    List.of(new Constraint("Web", "Core \"é\" <x>/Api"), new Constraint("Web", "Core \"é\" <x>")));

  @Test
  void it_should_be_the_revision_the_sonarqube_cli_computes_for_the_same_models() {
    // Same models and hash as the revisionOf test in sonarqube-cli (tests/unit/core/architecture/intended-model.test.ts): keep both in sync.
    assertThat(IntendedRevision.of(List.of(TS, JAVA))).isEqualTo("4f1baa7d43ee72995a008af71ce75927b7c3cfc36e9fc7a6b2c94be212284935");
  }

  @Test
  void it_should_ignore_language_pattern_and_constraint_order_but_not_group_order() {
    var reordered = new IntendedArchitecture("namespace", "java", JAVA.groups(), JAVA.constraints().reversed());
    assertThat(IntendedRevision.of(List.of(reordered, TS))).isEqualTo(IntendedRevision.of(List.of(TS, JAVA)));

    var swapped = new IntendedArchitecture("namespace", "java", JAVA.groups().reversed(), JAVA.constraints());
    assertThat(IntendedRevision.of(List.of(TS, swapped))).isNotEqualTo(IntendedRevision.of(List.of(TS, JAVA)));
  }

  @Test
  void it_should_sort_perspectives_with_the_same_language_by_qualifier() {
    var namespace = new IntendedArchitecture("namespace", "java", List.of(), List.of());
    var file = new IntendedArchitecture("file", "java", List.of(), List.of());
    var expected = "4db11f0eaccbd703960dc6141001dce437cebcd9ab08edf8ca7827da52bdd1c5";

    assertThat(IntendedRevision.of(List.of(namespace, file))).isEqualTo(expected);
    assertThat(IntendedRevision.of(List.of(file, namespace))).isEqualTo(expected);
  }

}
