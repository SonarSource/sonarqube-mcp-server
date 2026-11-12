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
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
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
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(SUMMARY));
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
      "{\"projectSummaries\":[" + SUMMARY.replace("\"total\":3", "\"total\":4") + "]}",
      "{\"projectSummaries\":[" + SUMMARY + "," + SUMMARY + "]}");
  }

  @ParameterizedTest
  @MethodSource("invalidCounts")
  void rejects_fractional_or_inconsistent_counts_and_duplicate_languages(String response) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @Test
  void reads_server_summary_using_the_public_server_route() {
    stubMainBranch(harness);
    var path = ArchitectureApi.SERVER_API_PREFIX + ArchitectureApi.PROJECT_SUMMARIES_PATH + "?organizationId="
      + ArchitectureApi.SERVER_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID + "&language=java";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson("{\"projectSummaries\":[" + SUMMARY + "]}")));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "summary", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("summaries").get(0)).isEqualTo(JsonParser.parseString(SUMMARY));
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
  @ValueSource(strings = {"component", "pageIndex", "category"})
  void rejects_parameters_that_do_not_apply_to_summary(String parameter) {
    var value = parameter.equals("pageIndex") ? 1 : parameter.equals("category") ? "flaw" : "a";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "summary", "projectKey", "my_project", parameter, value));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"projectSummaries\":[null]}", "{\"projectSummaries\":[{\"language\":\"java\"}]}"})
  void rejects_invalid_response(String response) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "summary", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }
}
