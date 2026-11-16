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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.NONE;

class ToolPolicyTest {

  private static final ToolCapabilities SERVER = new ToolCapabilities(false, false, false, false, false, NONE);

  @Test
  void it_should_allow_both_transports_by_default() {
    var policy = ToolPolicy.of(ToolCategory.ISSUES);

    assertThat(policy.isSupported(TransportMode.STDIO, SERVER)).isTrue();
    assertThat(policy.isSupported(TransportMode.HTTP, SERVER)).isTrue();
  }

  @Test
  void it_should_check_transport_before_requirements() {
    var evaluated = new AtomicBoolean();
    var original = ToolPolicy.of(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    var stdio = original.stdioOnly().requires(capabilities -> {
      evaluated.set(true);
      return capabilities.server();
    });

    assertThat(stdio.isSupported(TransportMode.HTTP, SERVER)).isFalse();
    assertThat(evaluated).isFalse();
    assertThat(stdio.isSupported(TransportMode.STDIO, SERVER)).isTrue();
    assertThat(stdio.categories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    assertThat(original.isSupported(TransportMode.HTTP, SERVER)).isTrue();
  }

  @Test
  void it_should_require_all_conditions() {
    var policy = ToolPolicy.of(ToolCategory.SYSTEM).requires(ToolCapabilities::server).requires(ToolCapabilities::architecture);

    assertThat(policy.isSupported(TransportMode.STDIO, SERVER)).isFalse();
    assertThat(policy.isSupported(TransportMode.STDIO, new ToolCapabilities(false, true, false, false, false, NONE))).isTrue();
  }

  @Test
  void it_should_copy_sets_and_expose_immutable_metadata() {
    var categories = new HashSet<>(Set.of(ToolCategory.ISSUES));
    var transports = new HashSet<>(Set.of(TransportMode.STDIO));
    var policy = new ToolPolicy(categories, transports, capabilities -> true);
    categories.clear();
    transports.clear();

    assertThat(policy.categories()).containsExactly(ToolCategory.ISSUES);
    assertThat(policy.transports()).containsExactly(TransportMode.STDIO);
    var policyCategories = policy.categories();
    var policyTransports = policy.transports();
    assertThatThrownBy(policyCategories::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(policyTransports::clear).isInstanceOf(UnsupportedOperationException.class);
  }
}
