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

import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureWriteRejectionTest {
  private static final String PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID;
  private static final String MODEL_PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "/11111111-1111-4111-8111-111111111111";
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 405, 409, 412, 422, 429})
  void it_should_relay_definite_write_rejections_without_recovery_reads(int status) {
    var next = INTENDED_JAVA_MODEL.replace("domain", "infra");
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(patch(MODEL_PATH).inScenario("rejected").whenScenarioStateIs("Started")
      .willSetStateTo("concurrent-save").willReturn(aResponse().withStatus(status).withBody("{\"message\":\"Request rejected\"}")));
    backend.stubFor(get(PATH).inScenario("rejected").whenScenarioStateIs("concurrent-save")
      .willReturn(okJson(intendedArchitectures(next))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", false));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).doesNotContain("Previous exported model", "could not be verified", "No automatic rollback");
    assertThat(backend.countRequestsContaining(PATH)).isEqualTo(2);
    assertThat(backend.getReceivedBodies("PATCH", MODEL_PATH)).hasSize(1);
    assertThat(backend.getReceivedBodies("POST", ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 404})
  void it_should_treat_unreadable_state_after_an_acknowledged_write_as_uncertain(int status) {
    var next = INTENDED_JAVA_MODEL.replace("domain", "infra");
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(patch(MODEL_PATH).inScenario("saved").whenScenarioStateIs("Started")
      .willSetStateTo("unreadable").willReturn(okJson(intendedArchitectures(next))));
    backend.stubFor(get(PATH).inScenario("saved").whenScenarioStateIs("unreadable").willReturn(aResponse().withStatus(status)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", false));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("could not be confirmed", "resulting state could not be read", "Previous exported model")
      .doesNotContain("Nothing was changed");
    assertThat(backend.countRequestsContaining(PATH)).isEqualTo(4);
    assertThat(backend.getReceivedBodies("PATCH", MODEL_PATH)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_reconcile_write_timeouts_without_repeating_the_write(boolean committed) {
    var next = INTENDED_JAVA_MODEL.replace("domain", "infra");
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(patch(MODEL_PATH).inScenario("timeout").whenScenarioStateIs("Started")
      .willSetStateTo("after-timeout").willReturn(aResponse().withStatus(408)));
    backend.stubFor(get(PATH).inScenario("timeout").whenScenarioStateIs("after-timeout")
      .willReturn(okJson(intendedArchitectures(committed ? next : INTENDED_JAVA_MODEL))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", false));

    assertThat(result.isError()).isEqualTo(!committed);
    assertThat(backend.countRequestsContaining(PATH)).isEqualTo(3);
    assertThat(backend.getReceivedBodies("PATCH", MODEL_PATH)).hasSize(1);
    if (!committed) {
      assertThat(textOf(result)).contains("Previous exported model", "No automatic rollback");
    }
  }
}
