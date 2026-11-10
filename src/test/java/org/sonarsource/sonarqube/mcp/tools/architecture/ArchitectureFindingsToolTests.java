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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonNull;
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
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
  @ValueSource(ints = {1, 200})
  void it_should_support_count_only_without_reporting_an_empty_analysis(int pageIndex) {
    stubEmptyPage(pageIndex, 0, 8);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", pageIndex, "pageSize", 0));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","findings":[],"page":{"pageIndex":%d,"pageSize":0,"total":8}}
      """.formatted(pageIndex));
  }

  @Test
  void it_should_explain_an_empty_later_page() {
    stubEmptyPage(200, 50, 144);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", 200));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","findings":[],"page":{"pageIndex":200,"pageSize":50,"total":144},
        "message":"Requested page 200 is past the last page (3). Use pageIndex=3."}
      """);
  }

  @Test
  void it_should_explain_no_visible_findings_without_claiming_a_clean_analysis() {
    stubEmptyPage(1, 50, 0);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No findings are visible").doesNotContain("clean", "healthy", "complete");
  }

  @Test
  void it_should_reject_findings_on_server_without_requests() {
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("intended");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/components/projects")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
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
  @CsvSource({"findings,category,FLAW", "findings,type,other", "findings,findingId,old-id", "findings,depth,1",
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
    assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
      .doesNotContain("Cannot invoke", "NullPointerException");
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

  @ParameterizedTest
  @CsvSource({"structuralDeviation,false", "language,false", "reason,false", "type,false",
    "type,true", "language,true", "reason,true"})
  void it_should_report_missing_or_null_metadata_as_a_neutral_api_error(String field, boolean missing) {
    var targetField = field.equals("structuralDeviation") ? "type" : field;
    var finding = JsonParser.parseString(DEVIATION).getAsJsonObject();
    var owner = targetField.equals("reason") ? finding.getAsJsonObject("details") : finding;
    if (missing) {
      owner.remove(targetField);
    } else {
      owner.add(targetField, JsonNull.INSTANCE);
    }
    stubFindingResponse(finding.toString());
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
      .doesNotContain("Cannot invoke", "NullPointerException");
  }

  @ParameterizedTest
  @CsvSource({"structuralDeviation,nodeKey", "relationshipDeviation,fromNodeKey", "relationshipDeviation,toNodeKey",
    "tangle,nodeKeys", "tangle,nodeCount", "weakTangle,nodeKeys", "weakTangle,nodeCount",
    "oversizedComponent,nodeKey", "splitResponsibility,nodeKey"})
  void it_should_reject_missing_or_null_required_details(String type, String field) {
    stubMainBranch(harness);
    var client = harness.newClient(CLOUD_ENV);
    for (var missing : new boolean[] {true, false}) {
      var finding = completeFinding(type);
      var details = finding.getAsJsonObject("details");
      if (missing) {
        details.remove(field);
      } else {
        details.add(field, JsonNull.INSTANCE);
      }
      stubFindingResponse(finding.toString());
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
      assertThat(result.isError()).as("%s: %s missing=%s", type, field, missing).isTrue();
      assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
        .doesNotContain("Cannot invoke", "NullPointerException");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"structuralDeviation", "relationshipDeviation", "tangle", "weakTangle", "oversizedComponent", "splitResponsibility"})
  void it_should_reject_missing_or_null_details_and_files(String type) {
    stubMainBranch(harness);
    var client = harness.newClient(CLOUD_ENV);
    for (var field : new String[] {"details", "files"}) {
      for (var missing : new boolean[] {true, false}) {
        var finding = completeFinding(type);
        if (missing) {
          finding.remove(field);
        } else {
          finding.add(field, JsonNull.INSTANCE);
        }
        stubFindingResponse(finding.toString());
        var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
        assertThat(result.isError()).as("%s: %s missing=%s", type, field, missing).isTrue();
        assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
          .doesNotContain("Cannot invoke", "NullPointerException");
      }
    }
  }

  @ParameterizedTest
  @CsvSource({"structuralDeviation,files", "tangle,nodeKeys", "weakTangle,nodeKeys"})
  void it_should_reject_null_list_elements(String type, String field) {
    var finding = completeFinding(type);
    var owner = field.equals("files") ? finding : finding.getAsJsonObject("details");
    owner.add(field, JsonParser.parseString("[\"app:web\",null]"));
    stubFindingResponse(finding.toString());
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
      .doesNotContain("Cannot invoke", "NullPointerException");
  }

  private static JsonObject completeFinding(String type) {
    var finding = JsonParser.parseString(DEVIATION).getAsJsonObject();
    finding.addProperty("type", type);
    finding.addProperty("category", ProjectFinding.categoryOf(type));
    finding.add("details", JsonParser.parseString("""
      {"reason":"misplacedComponent","nodeKey":"app:web","fromNodeKey":"app:web","toNodeKey":"app:db",
        "nodeKeys":["app:web","app:db"],"nodeCount":2}
      """));
    if (type.equals("relationshipDeviation")) {
      finding.getAsJsonObject("details").addProperty("reason", "disallowedDependency");
    }
    return finding;
  }

  @Test
  void it_should_skip_future_rows_before_deserializing_their_payloads() {
    var futureRows = "{\"type\":\"futureType\"},"
      + "{\"type\":\"futureType\",\"details\":{\"nodeCount\":{}},\"files\":{}},"
      + "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"futureLanguage\"},"
      + "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\","
      + "\"details\":{\"reason\":\"futureReason\",\"nodeCount\":{}},\"files\":{}}";
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(PATH))
      .willReturn(okJson(page(DEVIATION + "," + futureRows, 1, 50, 5))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var output = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(output.getAsJsonArray("findings")).hasSize(1);
    assertThat(output.get("skippedFindings").getAsInt()).isEqualTo(4);
    assertThat(output.getAsJsonObject("page").get("total").getAsInt()).isEqualTo(5);
  }

  private void stubFindingResponse(String finding) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(PATH))
      .willReturn(okJson(page(finding, 1, 50, 1))));
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
    assertThat(textOf(result)).contains("input validation failed");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/organizations/organizations")).hasSize(orgRequests);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  @Test
  void it_should_skip_future_values_and_preserve_backend_totals() {
    stubMainBranch(harness);
    var rows = String.join(",", DEVIATION, DEVIATION.replace("structuralDeviation", "futureType"),
      DEVIATION.replace("java", "futureLanguage"), DEVIATION.replace("misplacedComponent", "futureReason"));
    var path = PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID
      + "&branchId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page(rows, 1, 50, 4))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
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
      {"type", "structuralDeviation", DEVIATION.replace("\"type\":\"structuralDeviation\"", "\"type\":5").replace("java", "futureLanguage")}
    };
  }

  @Test
  void it_should_reject_a_missing_discriminator_before_applying_filters() {
    var row = DEVIATION.replace("\"type\":\"structuralDeviation\",", "").replace("java", "futureLanguage");
    stubFindingResponse(row);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "type", "structuralDeviation"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.")
      .doesNotContain("Cannot invoke", "NullPointerException");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_skip_future_values_that_match_active_filters(boolean futureReason) {
    stubMainBranch(harness);
    var row = futureReason ? DEVIATION.replace("misplacedComponent", "futureReason") : DEVIATION.replace("structuralDeviation", "futureType");
    var path = PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID
      + "&branchId=" + MAIN_BRANCH_ID + "&category=deviation" + (futureReason ? "&type=structuralDeviation" : "") + "&language=java&pageIndex=1&pageSize=50";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page(row, 1, 50, 1))));
    var arguments = new HashMap<String, Object>(Map.of("target", "findings", "projectKey", "my_project", "category", "deviation", "language", "java"));
    if (futureReason) {
      arguments.put("type", "structuralDeviation");
    }
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, arguments);
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

  @Test
  void it_should_refuse_findings_for_an_unanalyzed_main_branch() {
    harness.getMockSonarQubeServer().stubFor(get("/api/project_branches/list?project=my_project")
      .willReturn(okJson("{\"branches\":[{\"name\":\"main\",\"isMain\":true,\"branchId\":\"unanalysed-id\"}]}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("selected branch", "has not been analyzed");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  @Test
  void it_should_cap_finding_lists_and_return_the_full_lists_for_a_matching_detail() {
    stubMainBranch(harness);
    var files = java.util.stream.IntStream.range(0, 12).mapToObj(i -> "\"src/F" + i + ".java\"").collect(java.util.stream.Collectors.joining(","));
    var keys = java.util.stream.IntStream.range(0, 12).mapToObj(i -> "\"node-" + i + "\"").collect(java.util.stream.Collectors.joining(","));
    var structural = "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":"
      + "{\"reason\":\"misplacedComponent\",\"nodeKey\":\"app:web\"},\"files\":[" + files + "]}";
    var tangle = "{\"type\":\"tangle\",\"category\":\"flaw\",\"language\":\"java\",\"details\":{\"nodeKeys\":[" + keys + "],\"nodeCount\":12},\"files\":[\"src/Tangle.java\"]}";
    var relationship = "{\"type\":\"relationshipDeviation\",\"category\":\"deviation\",\"language\":\"java\",\"details\":"
      + "{\"reason\":\"disallowedDependency\",\"fromNodeKey\":\"app:web\",\"toNodeKey\":\"app:db\"},\"files\":[" + files + "]}";
    harness.getMockSonarQubeServer().stubFor(get(PATH + SCOPE + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(String.join(",", structural, tangle, relationship), 1, 50, 3))));
    var client = harness.newClient(CLOUD_ENV);

    var listed = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(listed.isError()).isFalse();
    var page = JsonParser.parseString(textOf(listed)).getAsJsonObject();
    assertThat(page.get("truncated").getAsBoolean()).isTrue();
    assertThat(page.get("message").getAsString()).contains("at most 10", "component");
    var findings = page.getAsJsonArray("findings");
    assertThat(findings.get(0).getAsJsonObject().getAsJsonArray("files")).hasSize(10);
    assertThat(findings.get(0).getAsJsonObject().get("fileCount").getAsInt()).isEqualTo(12);
    assertThat(findings.get(0).getAsJsonObject().get("filesTruncated").getAsBoolean()).isTrue();
    var tangleDetails = findings.get(1).getAsJsonObject().getAsJsonObject("details");
    assertThat(tangleDetails.getAsJsonArray("nodeKeys")).hasSize(10);
    assertThat(tangleDetails.get("nodeKeyCount").getAsInt()).isEqualTo(12);
    assertThat(tangleDetails.get("nodeKeysTruncated").getAsBoolean()).isTrue();

    var byComponent = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "component", "app:web"));
    var componentJson = JsonParser.parseString(textOf(byComponent)).getAsJsonObject();
    assertThat(byComponent.isError()).isFalse();
    assertThat(componentJson.has("truncated")).isFalse();
    assertThat(componentJson.getAsJsonArray("findings")).hasSize(2);
    componentJson.getAsJsonArray("findings").forEach(finding -> assertThat(finding.getAsJsonObject().getAsJsonArray("files")).hasSize(12));

    var byDependency = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "from", "app:web", "to", "app:db"));
    var dependencyJson = JsonParser.parseString(textOf(byDependency)).getAsJsonObject();
    assertThat(byDependency.isError()).isFalse();
    assertThat(dependencyJson.getAsJsonArray("findings")).hasSize(1);
    assertThat(dependencyJson.getAsJsonArray("findings").get(0).getAsJsonObject().getAsJsonArray("files")).hasSize(12);
  }

  @Test
  void it_should_reject_a_findings_page_above_50_before_network() {
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageSize", 51));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("page size must be between 0 and 50");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  private void stubEmptyPage(int pageIndex, int pageSize, int total) {
    stubMainBranch(harness);
    var path = PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID
      + "&branchId=" + MAIN_BRANCH_ID + "&pageIndex=" + pageIndex + "&pageSize=" + pageSize;
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(page("", pageIndex, pageSize, total))));
  }

  private static String page(String findings, int pageIndex, int pageSize, int total) {
    return "{\"findings\":[" + findings + "],\"page\":{\"pageIndex\":" + pageIndex + ",\"pageSize\":" + pageSize + ",\"total\":" + total + "}}";
  }
}
