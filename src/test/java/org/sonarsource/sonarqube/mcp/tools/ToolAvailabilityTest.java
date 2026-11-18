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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.IDE;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.NONE;

class ToolAvailabilityTest {

  private static final ToolCapabilities SERVER = new ToolCapabilities(false, false, false, false, false, NONE);

  @Test
  void it_should_allow_both_transports_by_default() {
    var availability = ToolAvailability.inCategory(ToolCategory.ISSUES);

    assertThat(availability.isAvailable(TransportMode.STDIO, SERVER)).isTrue();
    assertThat(availability.isAvailable(TransportMode.HTTP, SERVER)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_restrict_availability_to_the_selected_deployment(boolean cloud) {
    var capabilities = new ToolCapabilities(cloud, false, false, false, false, NONE);
    var original = ToolAvailability.inCategory(ToolCategory.PROJECTS);
    var cloudOnly = original.cloudOnly();
    var serverOnly = original.serverOnly();

    for (var transport : TransportMode.values()) {
      assertThat(cloudOnly.isAvailable(transport, capabilities)).isEqualTo(cloud);
      assertThat(serverOnly.isAvailable(transport, capabilities)).isEqualTo(!cloud);
      assertThat(original.isAvailable(transport, capabilities)).isTrue();
    }
  }

  @ParameterizedTest
  @EnumSource(AnalysisMode.class)
  void it_should_require_ide_analysis_mode(AnalysisMode mode) {
    var availability = ToolAvailability.inCategories(ToolCategory.ANALYSIS, ToolCategory.IDE).stdioOnly().requiresIdeAnalysis();
    var capabilities = new ToolCapabilities(false, false, false, false, false, mode);

    assertThat(availability.isAvailable(TransportMode.STDIO, capabilities)).isEqualTo(mode == IDE);
    assertThat(availability.isAvailable(TransportMode.HTTP, capabilities)).isFalse();
  }

  @Test
  void it_should_check_transport_before_requirements() {
    var evaluated = new AtomicBoolean();
    var original = ToolAvailability.inCategories(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    var stdio = original.stdioOnly().requires(capabilities -> {
      evaluated.set(true);
      return capabilities.server();
    });

    assertThat(stdio.isAvailable(TransportMode.HTTP, SERVER)).isFalse();
    assertThat(evaluated).isFalse();
    assertThat(stdio.isAvailable(TransportMode.STDIO, SERVER)).isTrue();
    assertThat(stdio.categories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    assertThat(original.isAvailable(TransportMode.HTTP, SERVER)).isTrue();
  }

  @Test
  void it_should_require_all_conditions() {
    var availability = ToolAvailability.inCategory(ToolCategory.SYSTEM).serverOnly().requires(ToolCapabilities::architecture);

    assertThat(availability.isAvailable(TransportMode.STDIO, SERVER)).isFalse();
    assertThat(availability.isAvailable(TransportMode.STDIO, new ToolCapabilities(false, true, false, false, false, NONE))).isTrue();
    assertThat(availability.isAvailable(TransportMode.STDIO, new ToolCapabilities(true, true, false, false, false, NONE))).isFalse();
  }

  @Test
  void it_should_copy_sets_and_expose_immutable_metadata() {
    var categories = new HashSet<>(Set.of(ToolCategory.ISSUES));
    var transports = new HashSet<>(Set.of(TransportMode.STDIO));
    var availability = new ToolAvailability(categories, transports, capabilities -> true);
    categories.clear();
    transports.clear();

    assertThat(availability.categories()).containsExactly(ToolCategory.ISSUES);
    assertThat(availability.transports()).containsExactly(TransportMode.STDIO);
    var availabilityCategories = availability.categories();
    var availabilityTransports = availability.transports();
    assertThatThrownBy(availabilityCategories::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(availabilityTransports::clear).isInstanceOf(UnsupportedOperationException.class);
  }
}
