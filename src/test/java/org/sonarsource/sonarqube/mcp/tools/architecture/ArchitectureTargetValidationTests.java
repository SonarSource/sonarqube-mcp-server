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
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureTargetValidationTests {

  @SonarQubeMcpServerTest
  void it_should_reject_unknown_read_targets_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(ArchitectureFixtures.CLOUD_ENV);

    for (var target : List.of("patterns", "unsupported")) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project"));

      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("current", "intended");
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_unsupported_server_targets_and_parameters_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    for (var target : List.of("current", "directives", "findings", "patterns", "unsupported")) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project"));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("intended");
    }
    for (var target : List.of("current", "directives", "findings", "patterns", "unsupported")) {
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME,
        Map.of("target", target, "projectKey", "my_project", "model", "[]", "dryRun", true));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("intended");
    }
    for (var unsupportedParam : List.of(
      Map.<String, Object>of("from", "app", "to", "other"),
      Map.<String, Object>of("pageIndex", 1),
      Map.<String, Object>of("pageSize", 10),
      Map.<String, Object>of("directiveId", "00000000-0000-4000-8000-000000000001"),
      Map.<String, Object>of("type", "tangle"),
      Map.<String, Object>of("category", "flaw"),
      Map.<String, Object>of("findingId", "old-id"))) {
      var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project"));
      args.putAll(unsupportedParam);
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, args);
      assertThat(result.isError()).isTrue();
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/components/projects")).isFalse();
  }

  @org.junit.jupiter.api.Test
  void it_should_enforce_server_target_and_parameter_restrictions_at_execution() {
    var api = org.mockito.Mockito.mock(org.sonarsource.sonarqube.mcp.serverapi.ServerApi.class);
    var getTool = new ArchitectureGetTool(() -> api, false, null);
    var setTool = new ArchitectureSetTool(() -> api, false, null);

    for (var target : List.of("current", "directives", "findings")) {
      var readArguments = new org.sonarsource.sonarqube.mcp.tools.Tool.Arguments(Map.of("target", target, "projectKey", "my_project"), null);
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> getTool.execute(readArguments))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Possible values: intended");
      var writeArguments = new org.sonarsource.sonarqube.mcp.tools.Tool.Arguments(
        Map.of("target", target, "projectKey", "my_project", "model", "[]", "dryRun", true), null);
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> setTool.execute(writeArguments))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Possible values: intended");
    }
    for (var unsupportedParam : List.of(
      Map.<String, Object>of("from", "app", "to", "other"),
      Map.<String, Object>of("pageIndex", 1),
      Map.<String, Object>of("pageSize", 10),
      Map.<String, Object>of("directiveId", "00000000-0000-4000-8000-000000000001"),
      Map.<String, Object>of("type", "tangle"),
      Map.<String, Object>of("category", "flaw"),
      Map.<String, Object>of("findingId", "old-id"))) {
      var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project"));
      args.putAll(unsupportedParam);
      var readArguments = new org.sonarsource.sonarqube.mcp.tools.Tool.Arguments(args, null);
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> getTool.execute(readArguments))
        .isInstanceOf(IllegalArgumentException.class);
    }
    org.mockito.Mockito.verifyNoInteractions(api);
  }

}
