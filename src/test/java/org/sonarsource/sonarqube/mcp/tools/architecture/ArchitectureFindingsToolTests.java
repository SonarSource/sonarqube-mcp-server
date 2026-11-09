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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ORGANIZATION_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureFindingsToolTests {
  private static final String PATH = ArchitectureApi.PROJECT_FINDINGS_PATH;
  private static final String SCOPE = "?organizationId=" + CLOUD_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID;
  private static final String DEVIATION = "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\","
      + "\"details\":{\"reason\":\"misplacedComponent\",\"nodeKey\":\"app:web\",\"misplacedComponentCount\":2},\"files\":[\"src/App.java\"]}";

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":{\"reason\":\"misplacedComponent\","
      + "\"nodeKey\":\"app:web\",\"misplacedComponentCount\":2},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":{\"reason\":\"unimplementedComponent\","
      + "\"nodeKey\":\"app:missing\"},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"relationshipDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":{\"reason\":\"disallowedDependency\","
      + "\"fromNodeKey\":\"app:web\",\"toNodeKey\":\"app:db\",\"dependencyCount\":3},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"relationshipDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":{\"reason\":\"interfaceViolation\","
      + "\"fromNodeKey\":\"app:web\",\"toNodeKey\":\"app:db\"},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"tangle\",\"category\":\"flaw\",\"language\":\"java\",\"details\":{\"nodeKeys\":[\"a\",\"b\"],\"nodeCount\":2},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"oversizedComponent\",\"category\":\"flaw\",\"language\":\"java\",\"details\":{\"nodeKey\":\"app\",\"leafArtifactCount\":10},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"weakTangle\",\"category\":\"smell\",\"language\":\"java\",\"details\":{\"nodeKeys\":[\"a\",\"b\"],\"nodeCount\":2},\"files\":[\"src/App.java\"]}",
    "{\"type\":\"splitResponsibility\",\"category\":\"smell\",\"language\":\"java\",\"details\":{\"nodeKey\":\"app\",\"fragmentCount\":3},\"files\":[\"src/App.java\"]}"
  })
  void it_should_read_all_finding_variants_with_details_and_without_ids(String finding) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50").willReturn(okJson(page(finding, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var output = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(output.getAsJsonArray("findings").get(0)).isEqualTo(JsonParser.parseString(finding));
    assertThat(output.getAsJsonObject("page").get("total").getAsInt()).isEqualTo(1);
  }

  @Test
  void it_should_combine_filters_and_preserve_repeated_findings_without_ids() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&category=deviation&type=structuralDeviation&language=java&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(DEVIATION + "," + DEVIATION, 1, 50, 2))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "category", "deviation", "type", "structuralDeviation", "language", "java"));
    assertThat(result.isError()).isFalse();
    var output = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(output.getAsJsonArray("findings")).hasSize(2);
  }

  @Test
  void it_should_read_a_later_page() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=2&pageSize=1").willReturn(okJson(page(DEVIATION, 2, 1, 3))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", 2, "pageSize", 1));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"pageIndex\" : 2", "\"pageSize\" : 1", "\"total\" : 3");
  }

  @ParameterizedTest
  @CsvSource({"true,1", "false,1", "true,200", "false,200"})
  void it_should_support_count_only_without_reporting_an_empty_analysis(boolean cloud, int pageIndex) {
    stubEmptyPage(cloud, pageIndex, 0, 8);
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", pageIndex, "pageSize", 0));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","findings":[],"page":{"pageIndex":%d,"pageSize":0,"total":8}}
      """.formatted(pageIndex));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_explain_an_empty_later_page(boolean cloud) {
    stubEmptyPage(cloud, 200, 50, 144);
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", 200));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","findings":[],"page":{"pageIndex":200,"pageSize":50,"total":144},
        "message":"Requested page 200 is past the last page (3). Use pageIndex=3."}
      """);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_explain_no_visible_findings_without_claiming_a_clean_analysis(boolean cloud) {
    stubEmptyPage(cloud, 1, 50, 0);
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No findings are visible").doesNotContain("clean", "healthy", "complete");
  }

  @Test
  void it_should_use_the_main_branch_on_server_without_a_project_uuid_lookup() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.SERVER_API_PREFIX + PATH + "?organizationId="
      + ArchitectureApi.SERVER_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(DEVIATION, 1, 50, 1))));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("misplacedComponent", "src/App.java");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/components/projects")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 500})
  void it_should_report_backend_errors_without_an_internal_architecture_fallback(int status) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50").willReturn(aResponse().withStatus(status)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/architecture/")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "{\"findings\":[]}",
    "{\"findings\":[],\"page\":{\"pageIndex\":2,\"pageSize\":50,\"total\":0}}",
    "{\"findings\":[null],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}"})
  void it_should_reject_invalid_pages(String response) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50").willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"findings,category,FLAW", "findings,type,other", "findings,findingId,old-id", "findings,component,app",
    "findings,directiveId,id", "current,category,flaw", "intended,type,tangle", "directives,type,tangle"})
  void it_should_reject_invalid_arguments_before_reading(String target, String name, String value) {
    var arguments = new HashMap<String, Object>(Map.of("target", target, "projectKey", "my_project", name, value));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("invalidFindings")
  void it_should_reject_invalid_finding_types_and_details(String finding) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50").willReturn(okJson(page(finding, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
  }

  static java.util.stream.Stream<String> invalidFindings() {
    return java.util.stream.Stream.of(
      DEVIATION.replace("deviation", "flaw"),
      DEVIATION.replace("structuralDeviation", ""),
      DEVIATION.replace("java", " "),
      DEVIATION.replace("misplacedComponent", ""),
      DEVIATION.replace("misplacedComponent", "interfaceViolation"),
      DEVIATION.replace("app:web", "" ).replace("\"nodeKey\":\"\"", "\"nodeKey\":{}"),
      DEVIATION.replace("\"misplacedComponentCount\":2", "\"misplacedComponentCount\":-1"),
      DEVIATION.replace("\"misplacedComponentCount\":2", "\"misplacedComponentCount\":2.5"),
      DEVIATION.replace("\"misplacedComponentCount\":2", "\"misplacedComponentCount\":\"invalid\""),
      DEVIATION.replace("\"src/App.java\"", "{}"),
      DEVIATION.replace("\"src/App.java\"", "null"));
  }

  @Test
  void it_should_ignore_unknown_fields_and_normalize_optional_null_counts() {
    stubMainBranch(harness);
    var finding = DEVIATION.replace("\"misplacedComponentCount\":2", "\"misplacedComponentCount\":null,\"unknownDetail\":true")
      .replace("\"files\":", "\"unknownField\":true,\"files\":");
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50").willReturn(okJson(page(finding, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("misplacedComponent", "src/App.java").doesNotContain("misplacedComponentCount", "unknownDetail", "unknownField");
  }

  @Test
  void it_should_reject_a_response_outside_the_requested_filter() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&category=flaw&pageIndex=1&pageSize=50").willReturn(okJson(page(DEVIATION, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "category", "flaw"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("does not match the requested filters");
  }

  @ParameterizedTest
  @CsvSource({"pageIndex,0", "pageIndex,-1", "pageSize,-1", "pageSize,1001"})
  void it_should_reject_out_of_range_pagination(String name, int value) {
    var client = harness.newClient(CLOUD_ENV);
    var orgRequests = harness.getMockSonarQubeServer().getReceivedBodies("GET", "/organizations/organizations").size();
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "missing-project", name, value));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Page must be >= 1 and page size must be between 0 and 1000.");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/organizations/organizations")).hasSize(orgRequests);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_skip_future_values_and_preserve_backend_totals(boolean cloud) {
    stubMainBranch(harness);
    var rows = String.join(",", DEVIATION, DEVIATION.replace("structuralDeviation", "futureType"),
      DEVIATION.replace("java", "futureLanguage"), DEVIATION.replace("misplacedComponent", "futureReason"));
    var organization = cloud ? CLOUD_ORGANIZATION_ID : ArchitectureApi.SERVER_ORGANIZATION_ID;
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + PATH + "?organizationId=" + organization
      + "&branchId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page(rows, 1, 50, 4))));
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var output = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(output.getAsJsonArray("findings")).hasSize(1);
    assertThat(output.getAsJsonObject("page").get("total").getAsInt()).isEqualTo(4);
    assertThat(output.get("skippedFindings").getAsInt()).isEqualTo(3);
    assertThat(output.get("message").getAsString()).contains("Skipped 3", "backend total is unchanged");
  }

  @ParameterizedTest
  @MethodSource("rawFilterMismatches")
  void it_should_reject_raw_filter_mismatches_before_skipping(String name, String filter, String row) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&" + name + "=" + filter + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(row, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", name, filter));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("does not match the requested filters");
  }

  private static Object[][] rawFilterMismatches() {
    var future = DEVIATION.replace("structuralDeviation", "futureType");
    return new Object[][] {
      {"type", "structuralDeviation", future},
      {"language", "java", DEVIATION.replace("java", "futureLanguage")},
      {"category", "deviation", future.replace("\"category\":\"deviation\"", "\"category\":\"flaw\"")},
      {"type", "structuralDeviation", DEVIATION.replace("structuralDeviation", "relationshipDeviation").replace("misplacedComponent", "futureReason")},
      {"language", "java", future.replace("\"language\":\"java\"", "\"language\":5")},
      {"language", "java", future.replace("\"language\":\"java\",", "")},
      {"category", "deviation", future.replace("\"category\":\"deviation\"", "\"category\":false")},
      {"category", "deviation", future.replace("\"category\":\"deviation\",", "")},
      {"type", "structuralDeviation", DEVIATION.replace("\"type\":\"structuralDeviation\"", "\"type\":5").replace("java", "futureLanguage")},
      {"type", "structuralDeviation", DEVIATION.replace("\"type\":\"structuralDeviation\",", "").replace("java", "futureLanguage")}
    };
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,false", "true,true", "false,true"})
  void it_should_skip_future_values_that_match_active_filters(boolean cloud, boolean futureReason) {
    stubMainBranch(harness);
    var row = futureReason ? DEVIATION.replace("misplacedComponent", "futureReason") : DEVIATION.replace("structuralDeviation", "futureType");
    var organization = cloud ? CLOUD_ORGANIZATION_ID : ArchitectureApi.SERVER_ORGANIZATION_ID;
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + PATH + "?organizationId=" + organization
      + "&branchId=" + MAIN_BRANCH_ID + "&category=deviation" + (futureReason ? "&type=structuralDeviation" : "") + "&language=java&pageIndex=1&pageSize=50";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page(row, 1, 50, 1))));
    var arguments = new HashMap<String, Object>(Map.of("target", "findings", "projectKey", "my_project", "category", "deviation", "language", "java"));
    if (futureReason) {
      arguments.put("type", "structuralDeviation");
    }
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).as(textOf(result)).isFalse();
    var output = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(output.getAsJsonArray("findings")).isEmpty();
    assertThat(output.get("skippedFindings").getAsInt()).isEqualTo(1);
    assertThat(output.getAsJsonObject("page").get("total").getAsInt()).isEqualTo(1);
  }

  @Test
  void it_should_reject_incompatible_finding_filters_before_requests() {
    var client = harness.newClient(CLOUD_ENV);
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "category", "smell", "type", "tangle"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("belongs to category flaw, not smell");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  @Test
  void it_should_explain_filtered_zero_results() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&category=smell&type=weakTangle&language=java&pageIndex=1&pageSize=50")
      .willReturn(okJson(page("", 1, 50, 0))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "category", "smell", "type", "weakTangle", "language", "java"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No findings match category=smell, type=weakTangle, language=java").doesNotContain("Check project access");
  }

  @Test
  void it_should_report_an_all_unsupported_page_without_claiming_zero_findings() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(DEVIATION.replace("structuralDeviation", "futureType"), 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("Skipped 1", "backend total is unchanged").doesNotContain("No findings are visible", "No findings on page");
  }

  @Test
  void it_should_reject_oversized_pages_even_when_future_rows_are_skipped() {
    stubMainBranch(harness);
    var future = DEVIATION.replace("structuralDeviation", "futureType");
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=1")
      .willReturn(okJson(page(future + "," + future, 1, 1, 2))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageSize", 1));
    assertThat(result.isError()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_refuse_findings_for_an_unanalyzed_main_branch(boolean cloud) {
    harness.getMockSonarQubeServer().stubFor(get("/api/project_branches/list?project=my_project")
      .willReturn(okJson("{\"branches\":[{\"name\":\"main\",\"isMain\":true,\"branchId\":\"unanalysed-id\"}]}")));
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("selected branch", "has not been analyzed");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", ArchitectureApi.SERVER_API_PREFIX + PATH)).isEmpty();
  }

  private void stubEmptyPage(boolean cloud, int pageIndex, int pageSize, int total) {
    stubMainBranch(harness);
    var organization = cloud ? CLOUD_ORGANIZATION_ID : ArchitectureApi.SERVER_ORGANIZATION_ID;
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + PATH + "?organizationId=" + organization
      + "&branchId=" + MAIN_BRANCH_ID + "&pageIndex=" + pageIndex + "&pageSize=" + pageSize;
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page("", pageIndex, pageSize, total))));
  }

  private static String page(String findings, int pageIndex, int pageSize, int total) {
    return "{\"findings\":[" + findings + "],\"page\":{\"pageIndex\":" + pageIndex + ",\"pageSize\":" + pageSize + ",\"total\":" + total + "}}";
  }
}
