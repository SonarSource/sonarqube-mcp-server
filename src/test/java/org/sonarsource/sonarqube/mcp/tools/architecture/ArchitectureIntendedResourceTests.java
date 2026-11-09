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

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedResource;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureIntendedResourceTests {
  private static final String PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH;
  private static final String ID = "11111111-1111-4111-8111-111111111111";
  private static final String READ = PATH + "?projectId=" + MAIN_BRANCH_ID;
  private static final String EMPTY_RESOURCE = "{\"intendedArchitectures\":[{\"id\":\"" + ID + "\",\"perspectives\":[]}]}";

  @SonarQubeMcpServerTest
  void it_should_reject_foreign_exports_before_preview_update_or_clear_on_cloud_and_server(SonarQubeMcpServerTestHarness harness) {
    var revision = IntendedRevision.of(IntendedModels.parse(INTENDED_JAVA_MODEL));
    for (var baseEnvironment : List.of(CLOUD_ENV, Map.<String, String>of())) {
      for (var projectArgument : List.of(Map.<String, Object>of("projectKey", "my_project"), Map.<String, Object>of())) {
        var environment = new HashMap<>(baseEnvironment);
        if (projectArgument.isEmpty()) {
          environment.put("SONARQUBE_PROJECT_KEY", "my_project");
        }
        var client = harness.newClient(environment);
        for (var dryRun : List.of(true, false)) {
          for (var models : List.of("[]", INTENDED_JAVA_MODEL.replace("domain", "infra"))) {
            var model = "{\"projectKey\":\"other_project\",\"revision\":\"" + revision + "\",\"models\":" + models + "}";
            var args = new HashMap<>(projectArgument);
            args.putAll(Map.of("target", "intended", "model", model, "dryRun", dryRun));
            var result = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("different project than 'my_project'", "Nothing was changed");
          }
        }
      }
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_invalid_or_conflicting_project_markers_before_backend_reads(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    for (var marker : List.of("null", "42", "true", "\"\"", "\" \"", "\"other_project\"")) {
      for (var property : List.of("projectKey", "project")) {
        var model = "{\"" + property + "\":" + marker + ",\"models\":[]}";
        var result = client.callTool(ArchitectureSetTool.TOOL_NAME,
          Map.of("target", "intended", "projectKey", "my_project", "model", model, "dryRun", false));
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("different project", "Nothing was changed");
      }
    }
    var conflicting = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model",
        "{\"projectKey\":\"my_project\",\"project\":\"other_project\",\"models\":[]}", "dryRun", false));
    assertThat(conflicting.isError()).isTrue();
    assertThat(textOf(conflicting)).contains("different project", "Nothing was changed");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_keep_matching_exports_and_untagged_templates_supported(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(READ).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);
    for (var prefix : List.of("", "\"projectKey\":\"my_project\",", "\"project\":\"my_project\",",
      "\"projectKey\":\"my_project\",\"project\":\"my_project\",")) {
      var model = "{" + prefix + "\"models\":" + INTENDED_JAVA_MODEL + "}";
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME,
        Map.of("target", "intended", "projectKey", "my_project", "model", model, "dryRun", false));
      assertThat(result.isError()).as(textOf(result)).isFalse();
      assertThat(textOf(result)).contains("unchanged");
    }
    var bare = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", true));
    assertThat(bare.isError()).as(textOf(bare)).isFalse();
    assertThat(textOf(bare)).contains("dry-run");
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_a_full_clear_without_a_revision(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Clearing the whole intended architecture needs the revision", "Nothing was changed");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).isEmpty();
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_clearing_an_empty_resource_without_a_revision(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).willReturn(okJson(EMPTY_RESOURCE)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Clearing the whole intended architecture needs the revision", "Nothing was changed");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_a_full_clear_with_a_stale_revision(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false, "revision", "stale"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("changed since revision stale", "nothing was changed");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_patch_an_existing_resource_with_zero_perspectives(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("empty-model").whenScenarioStateIs("Started").willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(patch(PATH + "/" + ID).inScenario("empty-model").willSetStateTo("saved").willReturn(okJson(intendedResource(INTENDED_JAVA_MODEL))));
    backend.stubFor(get(READ).inScenario("empty-model").whenScenarioStateIs("saved").willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", false));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced");
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + ID)).hasSize(1);
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_delete_an_existing_empty_resource_only_on_apply(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("clear").whenScenarioStateIs("Started").willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("clear").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    backend.stubFor(get(READ).inScenario("clear").whenScenarioStateIs("deleted").willReturn(okJson("{\"intendedArchitectures\":[]}")));
    var client = harness.newClient(CLOUD_ENV);
    var preview = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", true));
    assertThat(preview.isError()).isFalse();
    assertThat(textOf(preview)).contains("resource will be deleted", "Applying this clear requires the revision", "architecture_get response with target intended");
    assertThat(backend.getReceivedBodies("DELETE", PATH)).isEmpty();
    var applied = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", false, "revision", IntendedRevision.of(List.of())));
    assertThat(applied.isError()).as(textOf(applied)).isFalse();
    assertThat(textOf(applied)).contains("cleared");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
  }

  @SonarQubeMcpServerTest
  void it_should_accept_a_whole_model_revision_from_a_filtered_read_for_clear(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("filtered-clear").whenScenarioStateIs("Started")
      .willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("filtered-clear").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    backend.stubFor(get(READ).inScenario("filtered-clear").whenScenarioStateIs("deleted")
      .willReturn(okJson("{\"intendedArchitectures\":[]}")));
    var client = harness.newClient(CLOUD_ENV);
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "language", "java", "depth", 1));
    assertThat(read.isError()).isFalse();
    var revision = JsonParser.parseString(textOf(read)).getAsJsonObject().get("revision").getAsString();
    assertThat(revision).isEqualTo(IntendedRevision.of(IntendedModels.parse(INTENDED_JAVA_MODEL)));
    var applied = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false, "revision", revision));
    assertThat(applied.isError()).as(textOf(applied)).isFalse();
    assertThat(textOf(applied)).contains("cleared");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_not_trust_malformed_readback_after_delete_or_repeat_the_write(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("clear").whenScenarioStateIs("Started").willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("clear").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    backend.stubFor(get(READ).inScenario("clear").whenScenarioStateIs("deleted").willReturn(okJson("{}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", false, "revision", IntendedRevision.of(IntendedModels.parse(INTENDED_JAVA_MODEL))));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("could not be confirmed", "No automatic rollback");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
  }

  @SonarQubeMcpServerTest
  void it_should_confirm_an_acknowledged_delete_after_a_transient_verification_failure(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("acknowledged-delete").whenScenarioStateIs("Started").willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("acknowledged-delete").willSetStateTo("verify").willReturn(aResponse().withStatus(204)));
    backend.stubFor(get(READ).inScenario("acknowledged-delete").whenScenarioStateIs("verify").willSetStateTo("verified")
      .willReturn(aResponse().withStatus(500)));
    backend.stubFor(get(READ).inScenario("acknowledged-delete").whenScenarioStateIs("verified").willReturn(okJson("{\"intendedArchitectures\":[]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false, "revision", IntendedRevision.of(List.of())));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("cleared");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
    assertThat(backend.countRequestsContaining(READ)).isEqualTo(4);
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_multiple_resources_before_writing(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).willReturn(okJson("{\"intendedArchitectures\":[" + intendedResource(INTENDED_JAVA_MODEL) + "," + intendedResource(INTENDED_JAVA_MODEL) + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", PATH)).isEmpty();
  }
  @SonarQubeMcpServerTest
  void it_should_keep_a_lost_delete_response_unconfirmed_even_when_search_returns_empty(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("lost-delete").whenScenarioStateIs("Started").willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(delete(PATH + "/" + ID).inScenario("lost-delete").willSetStateTo("hidden").willReturn(aResponse().withStatus(500)));
    backend.stubFor(get(READ).inScenario("lost-delete").whenScenarioStateIs("hidden").willReturn(okJson("{\"intendedArchitectures\":[]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", false, "revision", IntendedRevision.of(List.of())));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("could not be confirmed", "No automatic rollback", "No intended architecture resource was visible on readback");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
    assertThat(backend.countRequestsContaining(READ)).isEqualTo(3);
  }

  @SonarQubeMcpServerTest
  void it_should_not_confuse_an_undeleted_empty_resource_with_absence(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(delete(PATH + "/" + ID).willReturn(aResponse().withStatus(204)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":[]}", "dryRun", false, "revision", IntendedRevision.of(List.of())));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("could not be confirmed", "No automatic rollback");
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + ID)).hasSize(1);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_a_recreated_empty_resource_before_writing(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(READ).inScenario("recreated").whenScenarioStateIs("Started").willSetStateTo("changed").willReturn(okJson(EMPTY_RESOURCE)));
    backend.stubFor(get(READ).inScenario("recreated").whenScenarioStateIs("changed")
      .willReturn(okJson(EMPTY_RESOURCE.replace(ID, "22222222-2222-4222-8222-222222222222"))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("changed before replacement");
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
  }

}
