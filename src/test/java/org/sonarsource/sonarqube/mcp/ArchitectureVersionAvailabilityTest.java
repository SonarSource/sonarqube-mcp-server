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
package org.sonarsource.sonarqube.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureVersionAvailabilityTest {
  @SonarQubeMcpServerTest
  void older_server_hides_architecture_but_keeps_other_tools(SonarQubeMcpServerTestHarness harness) {
    var names = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture,issues", "SONARQUBE_VERSION", "2026.5.4"))
      .listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(names).doesNotContain("architecture_get", "architecture_set").contains("search_sonar_issues_in_projects");
  }

  @SonarQubeMcpServerTest
  void supported_server_exposes_architecture(SonarQubeMcpServerTestHarness harness) {
    var names = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture", "SONARQUBE_VERSION", "2026.6"))
      .listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(names).contains("architecture_get", "architecture_set");
  }

  @SonarQubeMcpServerTest
  void cloud_exposes_architecture(SonarQubeMcpServerTestHarness harness) {
    var names = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture", "SONARQUBE_ORG", "org"))
      .listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(names).contains("architecture_get", "architecture_set");
  }

  @SonarQubeMcpServerTest
  void oldest_supported_server_hides_architecture(SonarQubeMcpServerTestHarness harness) {
    var names = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture", "SONARQUBE_VERSION", "2025.1"))
      .listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(names).isNotEmpty().doesNotContain("architecture_get", "architecture_set");
  }

  @SonarQubeMcpServerTest
  void community_build_hides_architecture(SonarQubeMcpServerTestHarness harness) {
    var names = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture", "SONARQUBE_VERSION", "26.11"))
      .listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(names).isNotEmpty().doesNotContain("architecture_get", "architecture_set");
  }
}
