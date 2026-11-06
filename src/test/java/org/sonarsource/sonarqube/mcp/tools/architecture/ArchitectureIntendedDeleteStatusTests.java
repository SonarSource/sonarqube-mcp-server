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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureIntendedDeleteStatusTests {
  private static final String PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH;
  private static final String ID = "11111111-1111-4111-8111-111111111111";
  private static final String READ = PATH + "?projectId=" + MAIN_BRANCH_ID;

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @CsvSource({"200,false", "200,true", "202,false", "202,true"})
  void it_should_explain_unexpected_successful_delete_status_without_confirming_clear(int status, boolean hidden) {
    var result = clearWithStatus(status, hidden);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("could not be confirmed", "No automatic rollback", "The Architecture API returned an unexpected response.");
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 500})
  void it_should_preserve_non_success_delete_errors(int status) {
    var result = clearWithStatus(status, false);
    assertThat(result.isError()).isTrue();
    var expected = switch (status) {
      case 401 -> "Not authorized";
      case 403 -> "Forbidden";
      default -> "Error 500";
    };
    assertThat(textOf(result)).contains(expected).doesNotContain("The Architecture API returned an unexpected response.");
  }

  private McpSchema.CallToolResult clearWithStatus(int status, boolean hidden) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var stored = intendedArchitectures(INTENDED_JAVA_MODEL);
    backend.stubFor(get(READ).inScenario("delete-status").whenScenarioStateIs("Started").willReturn(okJson(stored)));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("delete-status").willSetStateTo("after").willReturn(aResponse().withStatus(status)));
    backend.stubFor(get(READ).inScenario("delete-status").whenScenarioStateIs("after")
      .willReturn(okJson(hidden ? "{\"intendedArchitectures\":[]}" : stored)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", false, "revision", IntendedRevision.of(IntendedModels.parse(INTENDED_JAVA_MODEL))));
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
    return result;
  }

}
