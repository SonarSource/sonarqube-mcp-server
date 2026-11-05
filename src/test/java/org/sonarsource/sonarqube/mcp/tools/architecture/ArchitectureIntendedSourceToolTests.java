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
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.PullRequestsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ORGANIZATION_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MODEL_LOCATION_PATH;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.modelLocation;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureIntendedSourceToolTests {

  private static final String LOOKUP = "/api/v2/components/projects?keys=my_project&pageSize=1";
  private static final String INTENDED = "/api/v2" + ArchitectureApi.INTENDED_ARCHITECTURES_PATH;
  private static final String GRAPHS = "/api/v2" + ArchitectureApi.GRAPHS_PATH;
  private static final String PR_ID = "pr-3-id";
  private static final String SERVER_MODEL = """
    [ { "type": "namespace", "language": "java", "groups": [ { "label": "web" }, { "label": "domain" } ],
        "constraints": [ { "from": "web", "to": "domain" } ] } ]""";
  private static final String FILE_GRAPH = """
    { "source": "FILE", "model": { "schema_version": "2.0.0", "perspectives": [ {
        "label": "modules", "qualifiers": "namespace", "language": "java",
        "groups": [ { "label": "api", "patterns": [ "api:**" ], "interface_access": true },
                    { "label": "parser", "patterns": [ "parser:**" ] },
                    { "label": "vertx", "patterns": [ "vertx:**" ] } ],
        "constraints": [ { "from": [ "parser", "vertx" ], "to": [ "api" ], "relation": "allow" } ] } ] } }""";
  private static final String SERVER_GRAPH = """
    { "source": "SERVER", "model": { "perspectives": [ { "qualifiers": "namespace", "language": "java", "groups": [ { "label": "web" } ],
        "constraints": [ { "from": [ "web" ], "to": [ "web" ] } ] } ], "constraints": [] } }""";
  private static final String FILE_MODELS_VIEW = """
    [ {
      "language": "java", "type": "namespace", "label": "modules",
      "groups": [ { "label": "api", "patterns": [ "api:**" ], "interfaceAccess": true },
                  { "label": "parser", "patterns": [ "parser:**" ] },
                  { "label": "vertx", "patterns": [ "vertx:**" ] } ],
      "constraints": [ { "from": "parser", "to": "api" }, { "from": "vertx", "to": "api" } ]
    } ]""";

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @Test
  void it_should_report_the_server_model_without_reading_the_analysis_when_the_setting_is_server() {
    stubProject("SERVER");

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("SERVER");
    assertThat(view.get("sourceSetting").getAsString()).isEqualTo("SERVER");
    assertThat(view.get("revision").getAsString()).isNotBlank();
    assertThat(view.getAsJsonArray("models")).hasSize(1);
    assertThat(view.has("message")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(GRAPHS)).isFalse();
  }

  @Test
  void it_should_return_the_analysed_file_model_when_the_main_analysis_used_the_file() {
    stubProject("DYNAMIC");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", FILE_GRAPH);

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.get("sourceSetting").getAsString()).isEqualTo("DYNAMIC");
    assertThat(view.has("revision")).isFalse();
    assertThat(view.getAsJsonArray("models")).isEqualTo(JsonParser.parseString(FILE_MODELS_VIEW));
    assertThat(view.get("message").getAsString()).isEqualTo(ArchitectureGetTool.FILE_MANAGED_MESSAGE);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(INTENDED)).isFalse();
  }

  @Test
  void it_should_match_the_setting_case_insensitively() {
    stubProject("file");

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.get("sourceSetting").getAsString()).isEqualTo("FILE");
  }

  @Test
  void it_should_say_that_no_analysis_read_the_file_yet_when_the_setting_is_file() {
    stubProject("FILE");

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.getAsJsonArray("models")).isEmpty();
    assertThat(view.get("message").getAsString()).contains("No analysis has read the architecture file yet.", ArchitectureGetTool.FILE_MANAGED_MESSAGE);
  }

  @Test
  void it_should_say_when_the_last_analysis_found_no_usable_file() {
    stubProject("FILE");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", "{\"source\":\"FILE\",\"model\":null}");

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.getAsJsonArray("models")).isEmpty();
    assertThat(view.get("message").getAsString()).contains("The last analysis found no usable architecture file");
  }

  @Test
  void it_should_report_the_server_model_when_the_analysis_used_it_or_none_exists() {
    stubProject("DYNAMIC");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", SERVER_GRAPH);

    assertThat(getIntended(Map.of()).get("source").getAsString()).isEqualTo("SERVER");

    harness.getMockSonarQubeServer().stubFor(get(graphsPath(MAIN_BRANCH_ID)).willReturn(aResponse().withStatus(404)));
    assertThat(getIntended(Map.of()).get("source").getAsString()).isEqualTo("SERVER");
  }

  @Test
  void it_should_fall_back_to_the_main_branch_model_for_a_pull_request_without_one() {
    stubProject("DYNAMIC");
    stubPullRequests();
    stubGraphs(PR_ID);
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", FILE_GRAPH);

    var view = getIntended(Map.of("pullRequest", "3"));

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.get("pullRequest").getAsString()).isEqualTo("3");
    assertThat(view.get("fromMainBranch").getAsBoolean()).isTrue();
  }

  @Test
  void it_should_read_the_model_of_the_requested_branch() {
    stubProject("DYNAMIC");
    stubGraphs("other-branch-id", "g-feature");
    stubGraph("g-feature", FILE_GRAPH);

    var view = getIntended(Map.of("branch", "feature"));

    assertThat(view.get("source").getAsString()).isEqualTo("FILE");
    assertThat(view.get("branch").getAsString()).isEqualTo("feature");
    assertThat(view.has("fromMainBranch")).isFalse();
  }

  @Test
  void it_should_report_constraints_it_cannot_show() {
    stubProject("DYNAMIC");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", FILE_GRAPH.replace("\"relation\": \"allow\" }", "\"relation\": \"allow\" }, { \"from\": [ \"api\" ], \"to\": [ \"vertx\" ], \"relation\": \"deny\" }"));

    var view = getIntended(Map.of());

    assertThat(view.get("partial").getAsBoolean()).isTrue();
    assertThat(view.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("constraints")).hasSize(2);
    assertThat(view.get("message").getAsString()).startsWith("1 constraints other than allow constraints are not shown");
  }

  @Test
  void it_should_report_an_unknown_source_and_still_return_the_server_model_when_the_analysis_cannot_be_read() {
    stubProject("DYNAMIC");
    harness.getMockSonarQubeServer().stubFor(get(graphsPath(MAIN_BRANCH_ID)).willReturn(aResponse().withStatus(500)));

    var view = getIntended(Map.of());

    assertThat(view.get("source").getAsString()).isEqualTo("UNKNOWN");
    assertThat(view.getAsJsonArray("models")).hasSize(1);
    assertThat(view.get("message").getAsString()).startsWith("Whether a repository file manages this model is unknown.");
  }

  @Test
  void it_should_treat_an_unreadable_setting_as_dynamic() {
    stubProject(null);
    harness.getMockSonarQubeServer().stubFor(get(MODEL_LOCATION_PATH).willReturn(aResponse().withStatus(403)));

    var view = getIntended(Map.of());

    assertThat(view.get("sourceSetting").getAsString()).isEqualTo("DYNAMIC");
    assertThat(view.get("source").getAsString()).isEqualTo("SERVER");
  }

  @Test
  void it_should_resolve_the_source_on_sonarqube_cloud() {
    harness.getMockSonarQubeServer().stubFor(get(MODEL_LOCATION_PATH).willReturn(okJson(modelLocation("DYNAMIC"))));
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.GRAPHS_PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID + "&branchId=" + MAIN_BRANCH_ID)
      .willReturn(okJson(summaries("g-main"))));
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.GRAPHS_PATH + "/g-main").willReturn(okJson(FILE_GRAPH)));

    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("source").getAsString()).isEqualTo("FILE");
  }

  @Test
  void it_should_explain_unknown_pull_requests_and_branches() {
    stubProject("DYNAMIC");
    stubPullRequests();

    var unknownPullRequest = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "pullRequest", "42"));
    var unknownBranch = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "branch", "nope"));

    assertThat(unknownPullRequest.isError()).isTrue();
    assertThat(textOf(unknownPullRequest)).contains("Pull request '42' was not found", "list_pull_requests");
    assertThat(unknownBranch.isError()).isTrue();
    assertThat(textOf(unknownBranch)).contains("Branch 'nope' was not found", "list_branches");
  }

  @Test
  void it_should_refuse_a_pull_request_without_an_id() {
    stubProject("DYNAMIC");
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(okJson("{\"pullRequests\":[{\"key\":\"3\",\"title\":\"t\",\"branch\":\"b\"}]}")));

    var result = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "pullRequest", "3"));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("did not return the id of pull request '3'");
  }

  @Test
  void it_should_reject_branch_and_pull_request_together_and_on_directives() {
    var both = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "branch", "feature", "pullRequest", "3"));
    var directives = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "branch", "feature"));

    assertThat(both.isError()).isTrue();
    assertThat(textOf(both)).contains("Cannot use 'branch' and 'pullRequest' together");
    assertThat(directives.isError()).isTrue();
    assertThat(textOf(directives)).contains("branch and pullRequest are not used for directives");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @Test
  void it_should_read_current_architecture_of_the_requested_branch() {
    stubMainBranch(harness);
    var current = "/api/v2" + ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=" + ArchitectureApi.SERVER_ORGANIZATION_ID + "&branchId=other-branch-id";
    for (var language : ArchitectureApi.LANGUAGES) {
      harness.getMockSonarQubeServer().stubFor(get(current + "&language=" + language).willReturn(okJson("{\"currentArchitectures\":[]}")));
    }

    var result = call(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "branch", "feature"));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("branchId=other-branch-id")).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("branchId=" + MAIN_BRANCH_ID)).isFalse();
  }

  @Test
  void it_should_refuse_intended_write_without_reading_or_patching_when_a_repository_file_manages_the_model() {
    stubProject("DYNAMIC");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", FILE_GRAPH);

    var result = set(false);

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).isEqualTo("The intended architecture of 'my_project' on the main branch is managed by a repository file "
      + "(sonar-architecture.json, .yml or .yaml in the project base directory; sonar.architecture.model.location=DYNAMIC). Nothing was changed. "
      + "Edit the file and open a pull request, or set sonar.architecture.model.location to SERVER to manage it here.");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(INTENDED)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_refuse_intended_writes_and_dry_runs_when_the_setting_is_file(boolean dryRun) {
    stubProject("FILE");

    var result = set(dryRun);

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("managed by a repository file", "sonar.architecture.model.location=FILE", "Nothing was changed");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(INTENDED)).isFalse();
  }

  @Test
  void it_should_refuse_intended_writes_when_the_source_cannot_be_determined() {
    stubProject("DYNAMIC");
    harness.getMockSonarQubeServer().stubFor(get(graphsPath(MAIN_BRANCH_ID)).willReturn(aResponse().withStatus(500)));

    var result = set(true);

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).startsWith("Could not determine whether a repository file manages the intended architecture of 'my_project' "
      + "(sonar.architecture.model.location=DYNAMIC), so nothing was changed.");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(INTENDED)).isFalse();
  }

  @Test
  void it_should_allow_intended_writes_when_the_analysis_used_no_file() {
    stubProject("DYNAMIC");
    stubGraphs(MAIN_BRANCH_ID, "g-main");
    stubGraph("g-main", SERVER_GRAPH);

    var result = set(true);

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("dry-run");
  }

  private void stubProject(String setting) {
    var backend = harness.getMockSonarQubeServer();
    if (setting != null) {
      backend.stubFor(get(MODEL_LOCATION_PATH).willReturn(okJson(modelLocation(setting))));
    }
    backend.stubFor(get(LOOKUP).willReturn(okJson("{\"projects\":[{\"key\":\"my_project\",\"id\":\"opaque-project-id\"}],\"page\":{\"total\":1}}")));
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").willReturn(okJson(intendedArchitectures(SERVER_MODEL))));
    stubMainBranch(harness);
  }

  private void stubPullRequests() {
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(okJson("{\"pullRequests\":[{\"key\":\"3\",\"title\":\"t\",\"branch\":\"b\",\"pullRequestId\":\"" + PR_ID + "\"}]}")));
  }

  private void stubGraphs(String branchId, String... intendedModelGraphIds) {
    harness.getMockSonarQubeServer().stubFor(get(graphsPath(branchId)).willReturn(okJson(summaries(intendedModelGraphIds))));
  }

  private void stubGraph(String id, String graph) {
    harness.getMockSonarQubeServer().stubFor(get(GRAPHS + "/" + id).willReturn(okJson(graph)));
  }

  private static String summaries(String... intendedModelGraphIds) {
    var graphs = new StringBuilder("{\"id\":\"structure\",\"type\":\"namespace_graph\"}");
    for (var id : intendedModelGraphIds) {
      graphs.append(",{\"id\":\"").append(id).append("\",\"type\":\"intended_models\",\"ecosystem\":\"java\"}");
    }
    return "{\"graphs\":[" + graphs + "]}";
  }

  private static String graphsPath(String branchId) {
    return GRAPHS + "?organizationId=" + ArchitectureApi.SERVER_ORGANIZATION_ID + "&branchId=" + branchId;
  }

  private JsonObject getIntended(Map<String, Object> extra) {
    var arguments = new HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project"));
    arguments.putAll(extra);
    var result = call(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).as(textOf(result)).isFalse();
    return JsonParser.parseString(textOf(result)).getAsJsonObject();
  }

  private McpSchema.CallToolResult set(boolean dryRun) {
    return call(ArchitectureSetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "model", SERVER_MODEL, "dryRun", dryRun));
  }

  private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
    return harness.newClient(Map.of()).callTool(tool, arguments);
  }

}
