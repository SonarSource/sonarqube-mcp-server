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
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "pageIndex", 2, "pageSize", 100));
    assertThat(read.isError()).isFalse();
    assertThat(textOf(read)).contains("000000000101");
    var result = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "," + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().get("skipped").getAsInt()).isEqualTo(2);
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("created")).isEmpty();
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", PATH)).isEmpty();
  }

  @Test
  void it_should_refuse_short_pages_even_when_later_pages_make_the_total_match() {
    stubMainBranch(harness);
    var first = IntStream.rangeClosed(1, 99).mapToObj(id -> saved(id, "app:" + id))
      .collect(java.util.stream.Collectors.joining(","));
    stubPage(1, first, 101);
    stubPage(2, saved(100, "app:100") + "," + saved(101, "app:101"), 101);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("public directives list changed");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @Test
  void it_should_refuse_creation_above_the_project_limit_even_with_duplicate_stored_identities() {
    stubMainBranch(harness);
    for (var page = 1; page <= 10; page++) {
      var firstId = (page - 1) * 100 + 1;
      var rows = IntStream.range(firstId, firstId + 100).mapToObj(id -> saved(id, "same:node"))
        .collect(java.util.stream.Collectors.joining(","));
      stubPage(page, rows, 1000);
    }
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("1000 directives per project");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
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

  @Test
  void it_should_default_perspective_and_report_existing_duplicate_ids() {
    stubMainBranch(harness);
    stubPage(1, saved(1, "app:a").replace("\"graphPerspective\":null", "\"graphPerspective\":\"default_perspective\""), 1);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", true));
    assertThat(result.isError()).isFalse();
    var value = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(value.get("skipped").getAsInt()).isEqualTo(1);
    assertThat(value.getAsJsonArray("skippedIds").get(0).getAsString()).endsWith("000000000001");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @Test
  void it_should_read_and_delete_long_stored_values_without_applying_input_limits() {
    stubMainBranch(harness);
    var id = "00000000-0000-4000-8000-000000000001";
    var path = PATH + "/" + id;
    var row = saved(1, "x".repeat(1100)).replace("\"description\":null", "\"description\":\"" + "d".repeat(10001) + "\"");
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson(row)));
    harness.getMockSonarQubeServer().stubFor(delete(path).willReturn(aResponse().withStatus(204)));
    var client = harness.newClient(CLOUD_ENV);
    assertThat(client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "directiveId", id)).isError()).isFalse();
    var result = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("delete", "[{\"id\":\"" + id + "\"}]"), "dryRun", false));
    assertThat(result.isError()).isFalse();
  }

  @Test
  void it_should_preview_then_create_only_missing_directives() {
    stubMainBranch(harness);
    stubPage(1, "", 0);
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(saved(1, "app:a"))));
    var client = harness.newClient(CLOUD_ENV);
    var preview = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "," + INPUT + "]"), "dryRun", true));
    assertThat(preview.isError()).isFalse();
    assertThat(textOf(preview)).contains("dry-run");
    assertThat(JsonParser.parseString(textOf(preview)).getAsJsonObject().get("skipped").getAsInt()).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
    var apply = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(apply.isError()).isFalse();
    assertThat(textOf(apply)).contains("applied", "000000000001");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 429})
  void it_should_relay_backend_rejections_without_retrying(int status) {
    stubMainBranch(harness);
    stubPage(1, "", 0);
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(status).withBody("{\"message\":\"Invalid node or permission\"}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Invalid node or permission");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).hasSize(1);
  }

  @Test
  void it_should_refuse_an_incomplete_list_before_creating_anything() {
    stubMainBranch(harness);
    stubPage(1, "", 1);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("public directives list changed");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
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

  @Test
  void it_should_refuse_a_changing_list_before_creating_anything() {
    stubMainBranch(harness);
    var first = IntStream.rangeClosed(1, 100).mapToObj(i -> saved(i, "app:" + i)).collect(java.util.stream.Collectors.joining(","));
    var second = IntStream.rangeClosed(102, 201).mapToObj(i -> saved(i, "app:" + i)).collect(java.util.stream.Collectors.joining(","));
    stubPage(1, first, 201);
    stubPage(2, second, 200);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("list changed while reading");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"wrong-node", "malformed", "server-error"})
  void it_should_report_an_uncertain_first_write_without_retrying(String response) {
    stubMainBranch(harness);
    stubPage(1, "", 0);
    var body = response.equals("wrong-node") ? saved(1, "app:other") : "{}";
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse()
      .withStatus(response.equals("server-error") ? 500 : 201).withBody(body)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("attempted change may already have been applied", "Inspect the current list before retrying");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).hasSize(1);
  }

  @Test
  void it_should_report_partial_creation_ids_without_retrying_or_rolling_back() {
    stubMainBranch(harness);
    stubPage(1, "", 0);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(saved(1, "app:a"))));
    backend.stubFor(post(PATH).withRequestBody(containing("app:second"))
      .willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Missing architecture permission\"}")));
    var input = "[" + INPUT + "," + INPUT.replace("app:a", "app:second") + "]";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", input), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("stopped after 1 confirmed writes", "00000000-0000-4000-8000-000000000001", "server rejected");
    assertThat(backend.getReceivedBodies("POST", PATH)).hasSize(2);
    assertThat(backend.getReceivedBodies("DELETE", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH)).isEmpty();
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
  @ValueSource(strings = {"project", "projectKey"})
  void it_should_refuse_an_export_from_another_project_before_reading(String field) {
    var input = "{\"" + field + "\":\"other\",\"directives\":[" + INPUT + "]}";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", input), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("different project");
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("GET", PATH)).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-fields", "wrong-project", "invalid-id"})
  void it_should_refuse_incomplete_or_wrong_project_responses_before_writing(String kind) {
    stubMainBranch(harness);
    var item = switch (kind) {
      case "missing-fields" -> "{}";
      case "wrong-project" -> saved(1, "app:a").replace(MAIN_BRANCH_ID, "other-project");
      default -> saved(1, "app:a").replace("00000000-0000-4000-8000-000000000001", "invalid");
    };
    stubPage(1, item, 1);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + INPUT + "]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @Test
  void it_should_replace_exported_identity_with_the_mcp_creation_origin() {
    stubMainBranch(harness);
    stubPage(1, "", 0);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(saved(1, "app:a"))));
    var exported = JsonParser.parseString(saved(999, "app:a")).getAsJsonObject();
    exported.addProperty("origin", "server-owned");
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("create", "[" + exported + "]"), "dryRun", false));
    assertThat(result.isError()).isFalse();
    var request = JsonParser.parseString(backend.getReceivedBodies("POST", PATH).getFirst()).getAsJsonObject();
    assertThat(request.has("id")).isFalse();
    assertThat(request.get("origin").getAsString()).isEqualTo("sonarqube-mcp-server");
    assertThat(request.get("graphPerspective").getAsString()).isEqualTo("default_perspective");
    assertThat(request.get("projectId").getAsString()).isEqualTo(MAIN_BRANCH_ID);
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

  @Test
  void it_should_report_a_timeout_without_repeating_the_write() {
    var api = mock(ArchitectureApi.class);
    var page = new ArchitectureApi.DirectivePage(List.of(),
      new ArchitectureApi.DirectivePageInfo(1, 100, 0));
    when(api.getDirectivePage("my_project", MAIN_BRANCH_ID, 1, 100)).thenReturn(page);
    var directives = ProjectDirectives.parse(changeFile("create", "[" + INPUT + "]"), "my_project");
    var directive = directives.create().getFirst();
    when(api.createDirective("my_project", MAIN_BRANCH_ID, directive))
      .thenThrow(new CompletionException(new TimeoutException("Timed out")));
    assertThatThrownBy(() -> ProjectDirectives.apply(api, "my_project", MAIN_BRANCH_ID, directives, false))
      .hasMessageContaining("attempted change may already have been applied");
    verify(api).createDirective("my_project", MAIN_BRANCH_ID, directive);
  }

  @Test
  void it_should_read_update_and_delete_explicit_directives_with_previews() {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var id = "00000000-0000-4000-8000-000000000001";
    var path = PATH + "/" + id;
    backend.stubFor(get(path).willReturn(okJson(saved(1, "app:a"))));
    var updated = saved(1, "app:a").replace("\"description\":null", "\"description\":\"updated\"");
    backend.stubFor(patch(path).willReturn(okJson(updated)));
    backend.stubFor(delete(path).willReturn(aResponse().withStatus(204)));
    var client = harness.newClient(CLOUD_ENV);
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "directiveId", id));
    assertThat(read.isError()).isFalse();
    assertThat(textOf(read)).contains(id, "existing-client");
    var model = "[{\"id\":\"" + id + "\",\"description\":\"updated\"}]";
    var preview = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("update", model), "dryRun", true));
    assertThat(preview.isError()).isFalse();
    assertThat(textOf(preview)).contains("dry-run", "updated");
    assertThat(backend.getReceivedBodies("PATCH", path)).isEmpty();
    var apply = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("update", model), "dryRun", false));
    assertThat(apply.isError()).isFalse();
    assertThat(backend.getReceivedBodies("PATCH", path)).hasSize(1);
    var body = JsonParser.parseString(backend.getReceivedBodies("PATCH", path).getFirst()).getAsJsonObject();
    assertThat(body.get("fromNodeKey").getAsString()).isEqualTo("app:a");
    assertThat(body.get("description").getAsString()).isEqualTo("updated");
    assertThat(body.get("origin").getAsString()).isEqualTo("existing-client");
    assertThat(body.has("projectId")).isFalse();
    model = "[{\"id\":\"" + id + "\"}]";
    var deletionPreview = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("delete", model), "dryRun", true));
    assertThat(deletionPreview.isError()).isFalse();
    assertThat(backend.getReceivedBodies("DELETE", path)).isEmpty();
    var deletion = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("delete", model), "dryRun", false));
    assertThat(deletion.isError()).isFalse();
    assertThat(textOf(deletion)).contains("applied", id);
    assertThat(backend.getReceivedBodies("DELETE", path)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "number", "unknown", "wrong-project", "missing"})
  void it_should_refuse_invalid_updates_without_writing(String kind) {
    stubMainBranch(harness);
    var id = "00000000-0000-4000-8000-000000000001";
    var body = saved(1, "app:a");
    if ("wrong-project".equals(kind)) {
      body = body.replace(MAIN_BRANCH_ID, "other-project");
    }
    harness.getMockSonarQubeServer().stubFor(get(PATH + "/" + id).willReturn(okJson(body)));
    var field = switch (kind) {
      case "null" -> ",\"description\":null";
      case "number" -> ",\"description\":42";
      case "unknown" -> ",\"surprise\":\"value\"";
      default -> "";
    };
    var model = "[{\"id\":\"" + ("missing".equals(kind) ? "not-an-id" : id) + "\"" + field + "}]";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("update", model), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", PATH + "/" + id)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 429, 500})
  void it_should_not_replay_failed_updates_or_deletes(int status) {
    stubMainBranch(harness);
    var id = "00000000-0000-4000-8000-000000000001";
    var path = PATH + "/" + id;
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(path).willReturn(okJson(saved(1, "app:a"))));
    backend.stubFor(patch(path).willReturn(aResponse().withStatus(status).withBody("{\"message\":\"rejected\"}")));
    backend.stubFor(delete(path).willReturn(aResponse().withStatus(status).withBody("{\"message\":\"rejected\"}")));
    var client = harness.newClient(CLOUD_ENV);
    var update = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("update", "[{\"id\":\"" + id + "\",\"description\":\"changed\"}]"), "dryRun", false));
    var deletion = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("delete", "[{\"id\":\"" + id + "\"}]"), "dryRun", false));
    assertThat(update.isError()).isTrue();
    assertThat(deletion.isError()).isTrue();
    assertThat(backend.getReceivedBodies("PATCH", path)).hasSize(1);
    assertThat(backend.getReceivedBodies("DELETE", path)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "replace", "42"})
  void it_should_reject_unsupported_directive_operations(String operation) {
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "operation", operation, "model", "[" + INPUT + "]", "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @Test
  void it_should_report_a_mismatched_update_response_as_unconfirmed() {
    stubMainBranch(harness);
    var id = "00000000-0000-4000-8000-000000000001";
    var path = PATH + "/" + id;
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(path).willReturn(okJson(saved(1, "app:a"))));
    backend.stubFor(patch(path).willReturn(okJson(saved(2, "app:a"))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", changeFile("update", "[{\"id\":\"" + id + "\",\"description\":\"changed\"}]"), "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Inspect directive " + id).doesNotContain("Nothing was changed");
    assertThat(backend.getReceivedBodies("PATCH", path)).hasSize(1);
  }

  @Test
  void it_should_preflight_preview_and_apply_mixed_changes() {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var first="00000000-0000-4000-8000-000000000001";
    var second="00000000-0000-4000-8000-000000000002";
    backend.stubFor(get(PATH+"/"+first).willReturn(okJson(saved(1,"app:a"))));
    backend.stubFor(get(PATH+"/"+second).willReturn(okJson(saved(2,"app:remove"))));
    stubPage(1,saved(1,"app:a")+","+saved(2,"app:remove"),2);
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(saved(3,"app:new"))));
    backend.stubFor(patch(PATH+"/"+first).willReturn(okJson(saved(1,"app:a").replace("\"description\":null","\"description\":\"updated\""))));
    backend.stubFor(delete(PATH+"/"+second).willReturn(aResponse().withStatus(204)));
    var json="{\"create\":["+INPUT.replace("app:a","app:new")+"],\"update\":[{\"id\":\""+first+"\",\"description\":\"updated\"}],\"delete\":[{\"id\":\""+second+"\"}]}";
    var client=harness.newClient(CLOUD_ENV);
    var preview=client.callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",json,"dryRun",true));
    assertThat(preview.isError()).isFalse();assertThat(textOf(preview)).contains("toCreate","changes","toDelete","updated");
    assertThat(backend.getReceivedBodies("POST",PATH)).isEmpty();assertThat(backend.getReceivedBodies("PATCH",PATH+"/"+first)).isEmpty();assertThat(backend.getReceivedBodies("DELETE",PATH+"/"+second)).isEmpty();
    var applied=client.callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",json,"dryRun",false));
    assertThat(applied.isError()).isFalse();assertThat(backend.getReceivedBodies("POST",PATH)).hasSize(1);assertThat(backend.getReceivedBodies("PATCH",PATH+"/"+first)).hasSize(1);assertThat(backend.getReceivedBodies("DELETE",PATH+"/"+second)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings={"[]","{\"create\":null}","{\"update\":null}","{\"delete\":null}","{\"unknown\":[]}","{\"delete\":[{\"id\":\"bad\"}]}"})
  void it_should_refuse_malformed_change_files_without_writing(String model) {
    var result=harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",model,"dryRun",false));
    assertThat(result.isError()).isTrue();assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST",PATH)).isEmpty();
  }

  @Test
  void it_should_reject_conflicting_ids_before_discovery() {
    var id="00000000-0000-4000-8000-000000000001";
    var model="{\"update\":[{\"id\":\""+id+"\"}],\"delete\":[{\"id\":\""+id+"\"}]}";
    var result=harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",model,"dryRun",false));
    assertThat(result.isError()).isTrue();assertThat(textOf(result)).contains("conflicting");
  }

  @Test
  void it_should_validate_late_updates_before_creating_anything() {
    stubMainBranch(harness);var id="00000000-0000-4000-8000-000000000001";
    harness.getMockSonarQubeServer().stubFor(get(PATH+"/"+id).willReturn(okJson(saved(1,"app:a"))));
    var model="{\"create\":["+INPUT.replace("app:a","app:new")+"],\"update\":[{\"id\":\""+id+"\",\"description\":null}]}";
    var result=harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",model,"dryRun",false));
    assertThat(result.isError()).isTrue();assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST",PATH)).isEmpty();
  }

  @Test
  void it_should_report_confirmed_creations_when_a_later_update_is_rejected() {
    stubMainBranch(harness);var id="00000000-0000-4000-8000-000000000001";var backend=harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH+"/"+id).willReturn(okJson(saved(1,"app:a"))));stubPage(1,saved(1,"app:a"),1);
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(saved(2,"app:new"))));
    backend.stubFor(patch(PATH+"/"+id).willReturn(aResponse().withStatus(403).withBody("{\"message\":\"rejected\"}")));
    var model="{\"create\":["+INPUT.replace("app:a","app:new")+"],\"update\":[{\"id\":\""+id+"\",\"description\":\"updated\"}]}";
    var result=harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,Map.of("target","directives","projectKey","my_project","model",model,"dryRun",false));
    assertThat(result.isError()).isTrue();assertThat(textOf(result)).contains("stopped after 1 confirmed writes","000000000002");
    assertThat(backend.getReceivedBodies("POST",PATH)).hasSize(1);assertThat(backend.getReceivedBodies("PATCH",PATH+"/"+id)).hasSize(1);assertThat(backend.getReceivedBodies("DELETE",PATH+"/"+id)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"existing", "batch", "pending-delete", "later-page"})
  void it_should_refuse_update_identity_collisions_before_any_write(String kind) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var first = "00000000-0000-4000-8000-000000000001";
    var second = "00000000-0000-4000-8000-000000000002";
    backend.stubFor(get(PATH + "/" + first).willReturn(okJson(saved(1, "app:a"))));
    backend.stubFor(get(PATH + "/" + second).willReturn(okJson(saved(2, "app:other"))));
    if ("later-page".equals(kind)) {
      var filler = IntStream.rangeClosed(3, 101).mapToObj(i -> saved(i, "app:" + i))
        .collect(java.util.stream.Collectors.joining(","));
      stubPage(1, saved(1, "app:a") + "," + filler, 101);
      stubPage(2, saved(2, "app:other"), 101);
    } else {
      stubPage(1, saved(1, "app:a") + "," + saved(2, "app:other"), 2);
    }
    var updates = "batch".equals(kind)
      ? "[{\"id\":\"" + first + "\",\"fromNodeKey\":\"app:new\"},{\"id\":\"" + second + "\",\"fromNodeKey\":\"app:new\"}]"
      : "[{\"id\":\"" + first + "\",\"fromNodeKey\":\"app:other\"}]";
    var client = harness.newClient(CLOUD_ENV);
    for (var creates : List.of("[]", "[" + INPUT.replace("app:a", "new:source") + "]")) {
      var model = "{\"create\":" + creates + ",\"update\":" + updates
        + ("pending-delete".equals(kind) ? ",\"delete\":[{\"id\":\"" + second + "\"}]" : "") + "}";
      for (var dryRun : List.of(true, false)) {
        var result = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", dryRun));
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("An update would duplicate another directive");
      }
    }
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + first)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + second)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + second)).isEmpty();
  }

  @Test
  void it_should_allow_an_update_to_a_new_directive_identity() {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var id = "00000000-0000-4000-8000-000000000001";
    backend.stubFor(get(PATH + "/" + id).willReturn(okJson(saved(1, "app:a"))));
    stubPage(1, saved(1, "app:a"), 1);
    backend.stubFor(patch(PATH + "/" + id).willReturn(okJson(saved(1, "app:new"))));
    var model = "{\"update\":[{\"id\":\"" + id + "\",\"fromNodeKey\":\"app:new\"}]}";
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", false));
    assertThat(result.isError()).isFalse();
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + id)).hasSize(1);
  }

  private static String changeFile(String operation, String rows) {
    var value = JsonParser.parseString(rows);
    var result = new com.google.gson.JsonObject();
    if (value.isJsonObject()) {
      result = value.getAsJsonObject();
      var directives = result.remove("directives");
      if (directives != null) { result.add(operation, directives); }
    } else { result.add(operation, value); }
    return result.toString();
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
