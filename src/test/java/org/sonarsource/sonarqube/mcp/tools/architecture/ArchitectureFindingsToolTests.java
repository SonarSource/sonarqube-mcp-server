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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureFindingsToolTests {
  private static final String PATH = ArchitectureApi.PROJECT_FINDINGS_PATH;
  private static final String SERVER_PATH = ArchitectureApi.SERVER_API_PREFIX + PATH;
  private static final String SERVER_LOOKUP = "/api/v2/components/projects?keys=my_project&pageSize=1";
  private static final String SERVER_PROJECT_ID = "server-project-id";
  private static final String DEVIATION = "{\"id\":\"finding-1\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"category\":\"DEVIATION\","
    + "\"message\":\"app:web depends on app:db\",\"components\":[\"app:web\",\"app:db\"],"
    + "\"relationships\":[{\"from\":\"app:web\",\"to\":\"app:db\"}],\"constraint\":\"web must not depend on db\",\"measures\":{\"weight\":3.0}}";

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(strings = {"DEVIATION", "FLAW", "SMELL"})
  void it_should_read_findings_of_a_category(String category) {
    stubMainBranch(harness);
    var finding = DEVIATION.replace("DEVIATION", category);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&category=" + category + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(finding, 1, 50, 1))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "category", category));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"category\" : \"" + category + "\"", "\"total\" : 1").doesNotContain("No findings are visible");
  }

  @Test
  void it_should_read_a_page_of_findings_and_report_the_total() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=2&pageSize=1")
      .willReturn(okJson(page(DEVIATION, 2, 1, 3))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "pageIndex", 2, "pageSize", 1));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"pageIndex\" : 2", "\"pageSize\" : 1", "\"total\" : 3");
  }

  @Test
  void it_should_read_one_finding_with_its_details() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "/finding-1").willReturn(okJson(DEVIATION)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-1"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"constraint\" : \"web must not depend on db\"", "\"from\" : \"app:web\"", "\"weight\" : 3.0",
      "\"components\" : [ \"app:web\", \"app:db\" ]");
  }

  @Test
  void it_should_keep_integer_measures_and_drop_unknown_fields() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "/finding-2").willReturn(okJson(
      "{\"id\":\"finding-2\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"category\":\"SMELL\",\"measures\":{\"fanIn\":42},\"unexpected\":\"value\"}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-2"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"fanIn\" : 42").doesNotContain("42.0", "unexpected");
  }

  @ParameterizedTest
  @CsvSource({"other-project,finding-1", MAIN_BRANCH_ID + ",finding-2"})
  void it_should_reject_a_finding_from_another_project_or_id(String projectId, String id) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "/finding-1").willReturn(okJson(
      DEVIATION.replace(MAIN_BRANCH_ID, projectId).replace("finding-1", id))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-1"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("different id or project");
  }

  @Test
  void it_should_explain_an_unknown_finding() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "/missing").willReturn(aResponse().withStatus(404)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "findings", "projectKey", "my_project", "findingId", "missing"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Finding missing was not found or is not accessible in the selected project.");
  }

  @Test
  void it_should_explain_an_empty_result() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50")
      .willReturn(okJson(page("", 1, 50, 0))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No findings are visible. Check project access and whether the project has been analyzed.");
  }

  @Test
  void it_should_explain_a_permission_error() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50")
      .willReturn(aResponse().withStatus(403)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("refusal to read the architecture of project 'my_project'");
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"projectFindings\":[]}",
    "{\"projectFindings\":[],\"page\":{\"pageIndex\":2,\"pageSize\":50,\"total\":0}}",
    "{\"projectFindings\":[null],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}",
    "{\"projectFindings\":[{\"id\":\"x\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"category\":\"COVERAGE\"}],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}",
    "{\"projectFindings\":[{\"id\":\"x\",\"projectId\":\"other\",\"category\":\"FLAW\"}],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}",
    "{\"projectFindings\":[{\"id\":\"x\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"category\":\"FLAW\"},{\"id\":\"x\",\"projectId\":\""
      + MAIN_BRANCH_ID + "\",\"category\":\"FLAW\"}],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":2}}",
    "{\"projectFindings\":[{\"id\":\"x\",\"projectId\":\"" + MAIN_BRANCH_ID + "\",\"category\":\"FLAW\",\"measures\":{\"m\":\"text\"}}],"
      + "\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}"
  })
  void it_should_reject_invalid_pages(String response) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=1&pageSize=50").willReturn(okJson(response)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).containsAnyOf("Invalid public findings", "empty finding", "unsupported category", "different id or project",
      "duplicate ids", "non-numeric measure");
  }

  @Test
  void it_should_advertise_findings_on_server_without_cloud_only_targets() {
    var schema = harness.newClient().listTools().stream().filter(tool -> ArchitectureGetTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow()
      .inputSchema().toString();
    assertThat(schema).contains("findings", "findingId", "category=", "pageIndex", "pageSize").doesNotContain("directiveId", "intended");
  }

  @Test
  void it_should_read_server_findings_with_the_public_project_id() {
    stubServerProject("{\"projects\":[{\"id\":\"" + SERVER_PROJECT_ID + "\",\"key\":\"my_project\"}],\"page\":{\"total\":1}}");
    harness.getMockSonarQubeServer().stubFor(get(SERVER_PATH + "?projectId=" + SERVER_PROJECT_ID + "&category=FLAW&pageIndex=1&pageSize=50")
      .willReturn(okJson(page(DEVIATION.replace(MAIN_BRANCH_ID, SERVER_PROJECT_ID).replace("DEVIATION", "FLAW"), 1, 50, 1))));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project", "category", "FLAW"));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("\"category\" : \"FLAW\"", "\"total\" : 1");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({SERVER_PROJECT_ID + ",false", MAIN_BRANCH_ID + ",true"})
  void it_should_read_one_server_finding_only_from_the_resolved_project(String returnedProjectId, boolean rejected) {
    stubServerProject("{\"projects\":[{\"id\":\"" + SERVER_PROJECT_ID + "\",\"key\":\"my_project\"}],\"page\":{\"total\":1}}");
    harness.getMockSonarQubeServer().stubFor(get(SERVER_PATH + "/finding-1").willReturn(okJson(DEVIATION.replace(MAIN_BRANCH_ID, returnedProjectId))));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-1"));
    assertThat(result.isError()).isEqualTo(rejected);
    assertThat(textOf(result)).contains(rejected ? "different id or project" : "\"constraint\" : \"web must not depend on db\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"projects\":[],\"page\":{\"total\":0}}", "{\"projects\":[{\"id\":\" \",\"key\":\"my_project\"}],\"page\":{\"total\":1}}"})
  void it_should_not_read_server_findings_when_the_project_id_is_not_resolved(String lookup) {
    stubServerProject(lookup);
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).containsAnyOf("Could not resolve the project", "invalid project");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @Test
  void it_should_explain_a_server_without_the_public_project_lookup() {
    harness.getMockSonarQubeServer().stubFor(get(SERVER_LOOKUP).willReturn(aResponse().withStatus(404)));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-1"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("does not expose the public project lookup required for architecture findings");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  private void stubServerProject(String body) {
    harness.getMockSonarQubeServer().stubFor(get(SERVER_LOOKUP).willReturn(okJson(body)));
  }

  @ParameterizedTest
  @CsvSource({
    "findings,category,COVERAGE,",
    "findings,findingId,finding-1,pageIndex",
    "findings,findingId,finding-1,category",
    "findings,component,app,",
    "current,findingId,finding-1,",
    "current,category,FLAW,",
    "findings,directiveId,00000000-0000-4000-8000-000000000001,"
  })
  void it_should_reject_invalid_arguments_before_reading(String target, String name, String value, String extra) {
    var arguments = new HashMap<String, Object>(Map.of("target", target, "projectKey", "my_project", name, value));
    if (extra != null) {
      arguments.put(extra, extra.equals("pageIndex") ? 1 : "FLAW");
    }
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", "/api/project_branches/list")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
  }

  private static String page(String findings, int pageIndex, int pageSize, int total) {
    return "{\"projectFindings\":[" + findings + "],\"page\":{\"pageIndex\":" + pageIndex + ",\"pageSize\":" + pageSize + ",\"total\":" + total + "}}";
  }
}
