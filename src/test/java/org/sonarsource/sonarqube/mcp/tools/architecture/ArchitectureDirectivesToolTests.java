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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureDirectivesToolTests {
  private static final String PATH = ArchitectureApi.PROJECT_DIRECTIVES_PATH;
  private static final String INPUT = """
    {"type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph","fromNodeKey":"app:a","toNodeKey":"app:b"}
    """.trim();
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @Test
  void it_should_read_paginated_directives_and_skip_duplicates_without_replacement() {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var firstPage = IntStream.rangeClosed(1, 100).mapToObj(i -> saved(i, "app:" + i)).collect(java.util.stream.Collectors.joining(","));
    stubPage(1, firstPage, 101);
    stubPage(2, saved(101, "app:a"), 101);
    var client = harness.newClient(CLOUD_ENV);
    var schema = client.listTools().stream().filter(tool -> ArchitectureGetTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow().inputSchema();
    assertThat(schema.toString()).contains("directiveId/pageIndex/pageSize apply only to directives");
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "pageIndex", 2, "pageSize", 100));
    assertThat(read.isError()).isFalse();
    assertThat(textOf(read)).contains("000000000101");
  }





  @Test
  void it_should_refuse_pages_larger_than_the_requested_size() {
    stubMainBranch(harness);
    var rows = IntStream.rangeClosed(1, 101).mapToObj(id -> saved(id, "app:" + id))
      .collect(java.util.stream.Collectors.joining(","));
    stubPage(1, rows, 101);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "pageSize", 100));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Invalid public directives page");
  }











  @ParameterizedTest
  @CsvSource({"1,0", "2,1", "1,3"})
  void it_should_allow_count_drift_on_single_page_reads(int pageIndex, int total) {
    stubMainBranch(harness);
    stubPage(pageIndex, saved(1, "app:a"), total);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "pageIndex", pageIndex, "pageSize", 100));
    assertThat(result.isError()).isFalse();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }







  @ParameterizedTest
  @ValueSource(strings = {"component", "depth", "language"})
  void it_should_reject_graph_filters_on_directives_before_reading(String filter) {
    var arguments = new HashMap<String, Object>();
    arguments.put("target", "directives");
    arguments.put("projectKey", "my_project");
    arguments.put(filter, filter.equals("depth") ? 1 : filter.equals("language") ? "java" : "app");
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("do not accept component, depth or language");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }







  @ParameterizedTest
  @ValueSource(ints = {0, -1, 1001})
  void it_should_refuse_invalid_page_sizes_before_discovery(int pageSize) {
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "pageSize", pageSize));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("page size must be between 1 and 1000");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }





























  private void stubPage(int index, String directives, int total) {
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=" + index + "&pageSize=100")
      .willReturn(okJson("{\"projectDirectives\":[" + directives + "],\"page\":{\"pageIndex\":" + index + ",\"pageSize\":100,\"total\":" + total + "}}")));
  }

  private static String saved(int id, String from) {
    return INPUT.substring(0, INPUT.length() - 1).replace("app:a", from) + ",\"id\":\"00000000-0000-4000-8000-" + String.format("%012d", id)
      + "\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"graphPerspective\":null,\"description\":null,\"origin\":\"existing-client\"}";
  }
}
