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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ORGANIZATION_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureSummaryToolTests {
  private static final String PATH = ArchitectureApi.PROJECT_SUMMARIES_PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID;
  private static final String SUMMARY = "{\"language\":\"java\",\"deviations\":{\"total\":3,\"structuralDeviation\":1,\"relationshipDeviation\":2},"
    + "\"flaws\":{\"total\":1,\"tangle\":0,\"oversizedComponent\":1},\"smells\":{\"total\":0,\"weakTangle\":0,\"splitResponsibility\":0}}";
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @Test
  void reads_public_summary() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("projectKey").getAsString()).isEqualTo("my_project");
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(SUMMARY));
  }

  @Test
  void preserves_largest_exact_count() {
    stubMainBranch(harness);
    var largeSummary = SUMMARY.replace("\"total\":3", "\"total\":9007199254740991")
      .replace("\"structuralDeviation\":1", "\"structuralDeviation\":9007199254740990")
      .replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":1");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + largeSummary + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(largeSummary));
  }

  @Test
  void reads_multiple_languages_without_a_language_filter() {
    stubMainBranch(harness);
    var pythonSummary = SUMMARY.replace("\"java\"", "\"py\"");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "," + pythonSummary + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries")).hasSize(2);
  }

  static java.util.stream.Stream<String> invalidCounts() {
    return java.util.stream.Stream.of(
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":3.5") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":\"3\"") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":null") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":true") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3,", "") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"structuralDeviation\":1", "\"structuralDeviation\":-1")
        .replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":4") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":9007199254740992")
        .replace("\"structuralDeviation\":1", "\"structuralDeviation\":9007199254740992")
        .replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":0") + "]}",
      "{\"projectSummaries\":[" + SUMMARY + "," + SUMMARY + "]}",
      "{}", "{\"projectSummaries\":[null]}", "{\"projectSummaries\":[{\"language\":\"java\"}]}");
  }

  @ParameterizedTest
  @MethodSource("invalidCounts")
  void rejects_invalid_counts_and_duplicate_languages(String response) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"branch", "pullRequest"})
  void rejects_non_main_scope_instead_of_returning_main_counts(String parameter) {
    var api = mock(ServerApi.class);
    var tool = new ArchitectureGetTool(() -> api, true, null);
    var arguments = new Tool.Arguments(Map.of("target", "summary", "projectKey", "my_project", parameter, "other"), null);
    assertThatThrownBy(() -> tool.execute(arguments))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Summary reads support the main branch only");
  }

  @Test
  void does_not_advertise_or_dispatch_summary_on_server() {
    var client = harness.newClient();
    var tool = client.listTools().stream().filter(t -> t.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();
    assertThat(tool.inputSchema().toString()).doesNotContain("summary");
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("project-summaries")).isFalse();
  }

  @Test
  void skips_future_languages_and_preserves_backend_totals() {
    stubMainBranch(harness);
    var inconsistent = SUMMARY.replace("\"total\":3", "\"total\":4");
    var future = SUMMARY.replace("\"java\"", "\"go\"");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + inconsistent + "," + future + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var json = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(json.get("skippedSummaries").getAsInt()).isEqualTo(1);
    assertThat(json.getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(inconsistent));
  }

  @Test
  void skips_unknown_language_without_decoding_its_evolving_counts() {
    stubMainBranch(harness);
    var future = "{\"language\":\"go\",\"deviations\":{\"total\":\"future\"}}";
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + future + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("skippedSummaries").getAsInt()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "true"})
  void rejects_non_string_languages(String language) {
    stubMainBranch(harness);
    var invalid = SUMMARY.replace("\"java\"", language);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + invalid + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  void empty_summary_is_not_a_zero_measurement() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No architecture summaries are visible");
  }

  @Test
  void reports_permission_errors() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(aResponse().withStatus(403)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"component", "depth", "pageIndex", "category"})
  void rejects_parameters_that_do_not_apply_to_summary(String parameter) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var value = parameter.equals("pageIndex") || parameter.equals("depth") ? 1 : parameter.equals("category") ? "flaw" : "a";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "summary", "projectKey", "my_project", parameter, value));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains(parameter.equals("category") ? "only used for findings" : parameter.equals("pageIndex") ? "Pagination parameters" : "Summary reads do not accept component or depth");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("project-summaries")).isFalse();
  }


}
