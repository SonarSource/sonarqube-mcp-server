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
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
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
  void it_should_reads_public_summary() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("projectKey").getAsString()).isEqualTo("my_project");
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(SUMMARY));
  }

  @Test
  void it_should_read_assertion_deviations_and_omit_them_when_the_server_does_not_report_them() {
    stubMainBranch(harness);
    var withAssertions = SUMMARY.replace("\"total\":3", "\"total\":4")
      .replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":2,\"assertionDeviation\":1");
    var pythonWithout = SUMMARY.replace("\"java\"", "\"py\"");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + withAssertions + "," + pythonWithout + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    var summaries = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries");
    assertThat(summaries.get(0)).isEqualTo(JsonParser.parseString(withAssertions));
    assertThat(summaries.get(1)).isEqualTo(JsonParser.parseString(pythonWithout));
    assertThat(summaries.get(1).getAsJsonObject().getAsJsonObject("deviations").has("assertionDeviation")).isFalse();
  }

  @Test
  void it_should_return_combined_counts_without_inventing_a_cross_language_score() {
    stubMainBranch(harness);
    var javaRow = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":50,\"coveredArtifacts\":1,\"totalArtifacts\":2}}");
    var pyRow = SUMMARY.replace("\"java\"", "\"py\"").replaceFirst("\\}$", ",\"coverage\":{\"coverage\":90,\"coveredArtifacts\":9,\"totalArtifacts\":10}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + javaRow + "," + pyRow + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var json = JsonParser.parseString(textOf(result)).getAsJsonObject();
    var aggregate = json.getAsJsonObject("overallCoverage");
    assertThat(!aggregate.has("coverage") || aggregate.get("coverage").isJsonNull()).isTrue();
    assertThat(aggregate.get("coveredArtifacts").getAsLong()).isEqualTo(10);
    assertThat(aggregate.get("totalArtifacts").getAsLong()).isEqualTo(12);
  }

  @Test
  void it_should_preserve_the_exact_single_language_score() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":33.33333333,\"coveredArtifacts\":1,\"totalArtifacts\":3}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var aggregate = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonObject("overallCoverage");
    assertThat(aggregate.get("coverage").getAsDouble()).isEqualTo(33.33333333);
  }

  @Test
  void it_should_omit_empty_coverage() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":null,\"coveredArtifacts\":null,\"totalArtifacts\":null}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var summary = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0).getAsJsonObject();
    assertThat(!summary.has("coverage") || summary.get("coverage").isJsonNull()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"coverage\":101}", "{\"coveredArtifacts\":-1}", "{\"coveredArtifacts\":1.5}", "{\"coveredArtifacts\":2,\"totalArtifacts\":1}"})
  void it_should_reject_malformed_coverage(String coverage) {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":" + coverage + "}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Invalid public architecture response");
  }

  @Test
  void it_should_follow_gson_for_a_numeric_string() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":\"50\",\"coveredArtifacts\":\"1\",\"totalArtifacts\":\"2\"}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var summary = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0).getAsJsonObject();
    assertThat(summary.getAsJsonObject("coverage").get("coverage").getAsDouble()).isEqualTo(50);
    assertThat(summary.getAsJsonObject("coverage").get("coveredArtifacts").getAsLong()).isEqualTo(1);
    assertThat(summary.getAsJsonObject("coverage").get("totalArtifacts").getAsLong()).isEqualTo(2);
  }

  @Test
  void it_should_return_artifact_counts_as_integers() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coveredArtifacts\":2.0,\"totalArtifacts\":1e2}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var coverage = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0).getAsJsonObject().getAsJsonObject("coverage");
    assertThat(coverage.get("coveredArtifacts")).hasToString("2");
    assertThat(coverage.get("totalArtifacts")).hasToString("100");
  }

  @Test
  void it_should_not_aggregate_a_weighted_score_as_a_mapped_artifact_ratio() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":41,\"coveredArtifacts\":10,\"totalArtifacts\":10}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var json = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(json.has("overallCoverage")).isFalse();
    assertThat(json.getAsJsonArray("summaries").get(0).getAsJsonObject().getAsJsonObject("coverage").get("coverage").getAsDouble()).isEqualTo(41);
  }

  @Test
  void it_should_return_zero_artifacts_without_a_percentage() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":null,\"coveredArtifacts\":0,\"totalArtifacts\":0}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var aggregate = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonObject("overallCoverage");
    assertThat(aggregate.get("totalArtifacts").getAsLong()).isZero();
    assertThat(!aggregate.has("coverage") || aggregate.get("coverage").isJsonNull()).isTrue();
  }

  @Test
  void it_should_not_aggregate_when_coverage_is_missing() {
    stubMainBranch(harness);
    var row = SUMMARY.replaceFirst("\\}$", ",\"coverage\":{\"coverage\":null,\"coveredArtifacts\":null,\"totalArtifacts\":null}}");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + row + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().has("overallCoverage")).isFalse();
  }

  @Test
  void it_should_preserves_largest_exact_count() {
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
  void it_should_reads_multiple_languages_without_a_language_filter() {
    stubMainBranch(harness);
    var pythonSummary = SUMMARY.replace("\"java\"", "\"py\"");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "," + pythonSummary + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries")).hasSize(2);
  }

  static Stream<Arguments> invalidCounts() {
    var responses = List.of(
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
      "{\"projectSummaries\":[" + SUMMARY.replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":2,\"assertionDeviation\":null") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":2,\"assertionDeviation\":-1") + "]}",
      "{\"projectSummaries\":[" + SUMMARY.replace("\"relationshipDeviation\":2", "\"relationshipDeviation\":2,\"assertionDeviation\":\"1\"") + "]}",
      "{}", "{\"projectSummaries\":[null]}", "{\"projectSummaries\":[{\"language\":\"java\"}]}");
    var expected = List.of("Invalid public architecture response", "Invalid public architecture response",
      "Invalid or inconsistent project summary counts", "Invalid public architecture response",
      "Invalid or inconsistent project summary counts", "Invalid public architecture response",
      "Invalid public architecture response", "Invalid project summary language", "Invalid public architecture response",
      "Invalid public architecture response", "Invalid public architecture response", "Invalid project summaries",
      "Invalid project summary language", "Invalid project summary counts");
    return IntStream.range(0, responses.size()).mapToObj(i -> Arguments.of(responses.get(i), expected.get(i)));
  }

  @ParameterizedTest
  @MethodSource("invalidCounts")
  void it_should_rejects_invalid_counts_and_duplicate_languages(String response, String expectedMessage) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains(expectedMessage);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("project-summaries")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"branch", "pullRequest"})
  void it_should_rejects_non_main_scope_instead_of_returning_main_counts(String parameter) {
    var api = mock(ServerApi.class);
    var tool = new ArchitectureGetTool(() -> api, true, null);
    var arguments = new Tool.Arguments(Map.of("target", "summary", "projectKey", "my_project", parameter, "other"), null);
    assertThatThrownBy(() -> tool.execute(arguments))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Summary reads support the main branch only");
  }

  @Test
  void it_should_does_not_advertise_or_dispatch_summary_on_server() {
    var client = harness.newClient();
    var tool = client.listTools().stream().filter(t -> t.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();
    assertThat(tool.inputSchema().toString()).doesNotContain("summary");
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("project-summaries")).isFalse();
  }

  @Test
  void it_should_skips_future_languages_and_preserves_backend_totals() {
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
  void it_should_skips_unknown_language_without_decoding_its_evolving_counts() {
    stubMainBranch(harness);
    var future = "{\"language\":\"go\",\"deviations\":{\"total\":\"future\"}}";
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + future + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("skippedSummaries").getAsInt()).isEqualTo(1);
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("message").getAsString())
      .isEqualTo("Skipped 1 summary result(s) with unsupported languages.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "true"})
  void it_should_rejects_non_string_languages(String language) {
    stubMainBranch(harness);
    var invalid = SUMMARY.replace("\"java\"", language);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[" + invalid + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  void it_should_empty_summary_is_not_a_zero_measurement() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson("{\"projectSummaries\":[]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No architecture summaries are visible");
  }

  @Test
  void it_should_filter_summaries_by_language() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "&language=java").willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("java");
  }

  @Test
  void it_should_describe_an_empty_language_filter() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "&language=ts").willReturn(okJson("{\"projectSummaries\":[]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project", "language", "ts"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No architecture summaries match language=ts");
  }

  @Test
  void it_should_reject_a_mismatched_language() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "&language=ts").willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project", "language", "ts"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Invalid project summary language");
  }

  @Test
  void it_should_reports_permission_errors() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(aResponse().withStatus(403)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"component", "depth", "pageIndex", "category"})
  void it_should_rejects_parameters_that_do_not_apply_to_summary(String parameter) {
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
