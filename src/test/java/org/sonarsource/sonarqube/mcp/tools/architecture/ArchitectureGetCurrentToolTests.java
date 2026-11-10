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
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
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
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.SMALL_JAVA_GRAPH;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.currentArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.cyclicJavaGraph;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.largeJavaGraph;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureGetCurrentToolTests {

  private static final String CLOUD_CURRENT = ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID
    + "&branchId=" + MAIN_BRANCH_ID + "&language=";

  @SonarQubeMcpServerTest
  void it_should_read_targeted_dependency_pages_on_cloud(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_ENV);
    var arguments = Map.<String, Object>of("target", "current", "projectKey", "my_project", "language", "java",
      "from", "app:com.acme.web", "to", "app:com.acme.domain", "pageSize", 1);
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(result.isError()).isFalse();
    var body = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(body.has("paging")).isTrue();
    assertThat(body.has("page")).isFalse();
    assertThat(body.getAsJsonArray("dependencies")).hasSize(1);
    assertThat(body.getAsJsonObject("paging").get("total").getAsInt()).isEqualTo(2);
    assertThat(body.getAsJsonObject("paging").get("hasNextPage").getAsBoolean()).isTrue();
    var nextArguments = new java.util.HashMap<>(arguments);
    nextArguments.put("pageIndex", 2);
    var next = JsonParser.parseString(textOf(client.callTool(ArchitectureGetTool.TOOL_NAME, nextArguments))).getAsJsonObject();
    assertThat(next.get("revision")).isEqualTo(body.get("revision"));
    assertThat(next.getAsJsonArray("dependencies")).hasSize(1);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_targeted_arguments_before_backend_reads(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    var cases = List.of(
      Map.entry(Map.<String, Object>of("from", "app"), "Use both from and to"),
      Map.entry(Map.<String, Object>of("from", "", "to", ""), "Use both from and to"),
      Map.entry(Map.<String, Object>of("from", "app", "to", "other", "component", "app"), "without component"),
      Map.entry(Map.<String, Object>of("from", "app", "to", "other", "pageSize", 21), "dependency page size"),
      Map.entry(Map.<String, Object>of("from", "app", "to", "other", "pageIndex", 1.5), "pageIndex"),
      Map.entry(Map.<String, Object>of("from", "app", "to", "other", "directiveId", "00000000-0000-4000-8000-000000000001"), "directiveId is only used for directives"));
    for (var example : cases) {
      var arguments = new java.util.HashMap<>(example.getKey());
      arguments.put("target", "current");
      arguments.put("projectKey", "my_project");
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains(example.getValue());
    }
    var intended = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "from", "app", "to", "other"));
    assertThat(intended.isError()).isTrue();
    assertThat(textOf(intended)).contains("Use both from and to with target current");
    for (var target : List.of("current", "intended")) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project", "pageIndex", 1));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("Pagination parameters are only used for directives, findings or current from/to queries");
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_report_no_current_architecture_for_targeted_reads(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    stubEmptyCurrent(harness);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java", "from", "app", "to", "other"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No current architecture is visible", "latest analysis").doesNotContain("revision");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_padded_dependency_keys_before_backend_reads(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    for (var field : List.of("from", "to")) {
      var arguments = new java.util.HashMap<String, Object>(Map.of("target", "current", "projectKey", "my_project", "from", "app", "to", "other"));
      arguments.put(field, " padded ");
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains(field + " must be an exact component key without surrounding whitespace");
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_current_reads_on_server_before_network(SonarQubeMcpServerTestHarness harness) {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java")))
      .isInstanceOf(io.modelcontextprotocol.spec.McpError.class);

    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/api/v2/architecture/current-architectures")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_expose_navigation_metadata_beyond_the_five_drill_down_hints(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var nodes = java.util.stream.IntStream.range(0, 10).mapToObj(i -> """
      {"id":%d,"name":"module%d","kind":"module","edges":[],"nodes":[{"id":%d,"name":"Leaf","kind":"class","edges":[]}]}
      """.formatted(i * 2, i, i * 2 + 1)).collect(java.util.stream.Collectors.joining(","));
    var graph = "[{\"nodeCount\":20,\"edgeCount\":0,\"nodes\":[" + nodes + "]}]";
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(graph))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    var response = JsonParser.parseString(textOf(result)).getAsJsonObject();
    var returned = response.getAsJsonArray("graphs").get(0).getAsJsonObject().getAsJsonArray("nodes");
    assertThat(returned).hasSize(10);
    returned.forEach(node -> {
      assertThat(node.getAsJsonObject().get("descendants").getAsInt()).isEqualTo(1);
      assertThat(node.getAsJsonObject().get("truncated").getAsBoolean()).isTrue();
    });
    assertThat(response.getAsJsonArray("drillDown")).hasSize(5);
  }

  @SonarQubeMcpServerTest
  void it_should_explain_a_leaf_without_claiming_it_is_capped(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "component", "app:com.acme.domain.Service", "language", "java"));

    var response = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(response.get("capped").getAsBoolean()).isFalse();
    assertThat(response.get("message").getAsString()).contains("is a leaf; there are no children below it");
    assertThat(response.getAsJsonArray("graphs").get(0).getAsJsonObject().getAsJsonArray("nodes")).isEmpty();
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    var tool = harness.newClient(CLOUD_ENV).listTools().stream().filter(t -> t.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.outputSchema()).isNull();
    assertThat(tool.inputSchema()).containsEntry("required", List.of("target", "projectKey"));
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Map<String, Object>>) tool.inputSchema().get("properties");
    assertThat(properties).containsKeys("target", "projectKey", "component", "depth", "language", "from", "to", "pageIndex", "pageSize", "directiveId", "type", "category");
    assertThat(properties.get("target")).containsEntry("enum", List.of("current", "intended", "directives", "findings"));
    assertThat(properties.get("language")).containsEntry("enum", List.of("java", "cs", "js", "ts", "py"));
  }

  @SonarQubeMcpServerTest
  void it_should_return_the_top_level_with_rolled_up_edges_and_query_every_language(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    stubEmptyCurrent(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "depth", 2));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "depth" : 2,
        "requestedDepth" : 2,
        "capped" : false,
        "graphs" : [ {
          "language" : "java",
          "totalNodes" : 6,
          "totalEdges" : 3,
          "nodesInScope" : 3,
          "edgesInScope" : 1,
          "nodes" : [
            { "key" : "app", "kind" : "module", "children" : 2, "descendants" : 5, "truncated" : false },
            { "key" : "app:com.acme.web", "kind" : "package", "children" : 1, "descendants" : 1, "truncated" : true },
            { "key" : "app:com.acme.domain", "kind" : "package", "children" : 2, "descendants" : 2, "truncated" : true }
          ],
          "edges" : [ { "from" : "app:com.acme.web", "to" : "app:com.acme.domain", "weight" : 4 } ],
          "feedbackPairs" : { "total" : 0, "heaviest" : [ ] },
          "outsideViewWeight" : 0,
          "depth" : 2,
          "capped" : false,
          "omittedNodes" : 0,
          "omittedEdges" : 0
        } ],
        "drillDown" : [
          { "language" : "java", "component" : "app:com.acme.domain", "descendants" : 2 },
          { "language" : "java", "component" : "app:com.acme.web", "descendants" : 1 }
        ]
      }""");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/current-architectures")).isEqualTo(5);
  }

  @SonarQubeMcpServerTest
  void it_should_drill_down_into_a_component(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "component", "app:com.acme.domain", "language", "java"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "component" : "app:com.acme.domain",
        "depth" : 1,
        "requestedDepth" : 1,
        "capped" : false,
        "graphs" : [ {
          "language" : "java",
          "totalNodes" : 6,
          "totalEdges" : 3,
          "nodesInScope" : 2,
          "edgesInScope" : 1,
          "nodes" : [
            { "key" : "app:com.acme.domain.Service", "kind" : "class", "children" : 0, "descendants" : 0, "truncated" : false },
            { "key" : "app:com.acme.domain.Repository", "kind" : "class", "children" : 0, "descendants" : 0, "truncated" : false }
          ],
          "edges" : [ { "from" : "app:com.acme.domain.Service", "to" : "app:com.acme.domain.Repository", "weight" : 2 } ],
          "feedbackPairs" : { "total" : 0, "heaviest" : [ ] },
          "outsideViewWeight" : 4,
          "depth" : 1,
          "capped" : false,
          "omittedNodes" : 0,
          "omittedEdges" : 0
        } ],
        "drillDown" : [ ]
      }""");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/current-architectures")).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_report_feedback_pairs_and_the_weight_to_nodes_outside_the_view(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures("""
      [ { "nodeCount": 4, "edgeCount": 3, "nodes": [
        { "id": 1, "name": "app", "kind": "module", "edges": [], "nodes": [
          { "id": 2, "name": "web", "kind": "package", "edges": [ { "toId": 3, "weight": 3 } ] },
          { "id": 3, "name": "domain", "kind": "package", "edges": [ { "toId": 2, "weight": 1 }, { "toId": 4, "weight": 5 } ] }
        ] },
        { "id": 4, "name": "lib", "kind": "module", "edges": [] }
      ] } ]"""))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "component", "app", "language", "java"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "component" : "app",
        "depth" : 1,
        "requestedDepth" : 1,
        "capped" : false,
        "graphs" : [ {
          "language" : "java",
          "totalNodes" : 4,
          "totalEdges" : 3,
          "nodesInScope" : 2,
          "edgesInScope" : 2,
          "nodes" : [
            { "key" : "app:web", "kind" : "package", "children" : 0, "descendants" : 0, "truncated" : false },
            { "key" : "app:domain", "kind" : "package", "children" : 0, "descendants" : 0, "truncated" : false }
          ],
          "edges" : [
            { "from" : "app:web", "to" : "app:domain", "weight" : 3 },
            { "from" : "app:domain", "to" : "app:web", "weight" : 1 }
          ],
          "feedbackPairs" : { "total" : 1, "heaviest" : [ { "from" : "app:web", "to" : "app:domain", "weight" : 4 } ] },
          "outsideViewWeight" : 5,
          "depth" : 1,
          "capped" : false,
          "omittedNodes" : 0,
          "omittedEdges" : 0
        } ],
        "drillDown" : [ ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_rank_rolled_up_package_cycles_and_count_external_dependencies(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(cyclicJavaGraph()))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "component", "core", "depth", 1, "language", "java"));

    var graph = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs").get(0).getAsJsonObject();
    assertThat(graph.get("feedbackPairs")).isEqualTo(JsonParser.parseString("""
      { "total" : 15, "heaviest" : [
        { "from" : "core:p0", "to" : "core:p1", "weight" : 121 },
        { "from" : "core:p0", "to" : "core:p2", "weight" : 110 },
        { "from" : "core:p0", "to" : "core:p3", "weight" : 99 },
        { "from" : "core:p1", "to" : "core:p2", "weight" : 99 },
        { "from" : "core:p0", "to" : "core:p4", "weight" : 88 }
      ] }"""));
    assertThat(graph.get("outsideViewWeight").getAsInt()).isEqualTo(22);
  }

  @SonarQubeMcpServerTest
  void it_should_join_directories_with_a_slash(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "ts").willReturn(okJson(currentArchitectures("""
      [ { "nodeCount": 2, "edgeCount": 0, "nodes": [
        { "id": 1, "name": "src", "kind": "directory", "edges": [], "nodes": [ { "id": 2, "name": "index.ts", "kind": "file", "edges": [] } ] }
      ] } ]"""))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "ts", "depth", 2));

    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs").get(0).getAsJsonObject()
      .getAsJsonArray("nodes").get(1).getAsJsonObject().get("key").getAsString()).isEqualTo("src/index.ts");
  }

  @SonarQubeMcpServerTest
  void it_should_prefer_the_separator_of_the_node_over_its_kind(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "ts").willReturn(okJson(currentArchitectures("""
      [ { "id": "graph-ts", "nodeCount": 2, "edgeCount": 0, "nodes": [
        { "id": 1, "name": "web", "kind": "module", "separator": "/", "edges": [], "nodes": [
          { "id": 2, "name": "app.ts", "kind": "file", "separator": "/", "edges": [] } ] }
      ] } ]"""))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "ts", "depth", 2));

    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs").get(0).getAsJsonObject()
      .getAsJsonArray("nodes").get(1).getAsJsonObject().get("key").getAsString()).isEqualTo("web/app.ts");
  }

  @SonarQubeMcpServerTest
  void it_should_cap_a_large_graph_and_suggest_drill_downs(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var largeGraph = largeJavaGraph(1000);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(largeGraph))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java", "depth", 2));

    var text = textOf(result);
    var response = JsonParser.parseString(text).getAsJsonObject();
    var graph = response.getAsJsonArray("graphs").get(0).getAsJsonObject();
    assertThat(result.isError()).isFalse();
    assertThat(response.get("capped").getAsBoolean()).isTrue();
    assertThat(graph.getAsJsonArray("nodes")).hasSize(1);
    assertThat(graph.get("depth").getAsInt()).isEqualTo(1);
    assertThat(graph.get("omittedNodes").getAsInt()).isEqualTo(1000);
    assertThat(response.get("depth").getAsInt()).isEqualTo(1);
    assertThat(response.get("requestedDepth").getAsInt()).isEqualTo(2);
    assertThat(graph.get("nodesInScope").getAsInt()).isEqualTo(1001);
    assertThat(graph.get("totalNodes").getAsInt()).isEqualTo(2001);
    assertThat(response.getAsJsonArray("drillDown")).hasSize(1);
    assertThat(response.getAsJsonArray("drillDown").get(0).getAsJsonObject().get("component").getAsString()).isEqualTo("big");
    assertThat(response.get("message").getAsString())
      .isEqualTo("Showing 1 of 1001 nodes in the requested scope and 0 of 0 dependencies between shown nodes. Call architecture_get with target current and a component from drillDown, or with a language, to narrow the view. "
        + "Omitted siblings have no continuation token and may remain undiscoverable; this is not a complete graph export.");
    assertThat(text.length()).isLessThan(largeGraph.length() / 4);
  }

  @SonarQubeMcpServerTest
  void it_should_report_undiscoverable_omitted_siblings_in_a_flat_capped_view(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var nodes = java.util.stream.IntStream.range(0, GraphSlicer.MAX_NODES + 1)
      .mapToObj(id -> "{\"id\":" + id + ",\"name\":\"root" + id + "\",\"kind\":\"module\",\"edges\":[]}")
      .collect(java.util.stream.Collectors.joining(","));
    var graph = "[{\"nodeCount\":151,\"edgeCount\":0,\"nodes\":[" + nodes + "]}]";
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(graph))));

    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    var response = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(result.isError()).isFalse();
    assertThat(response.getAsJsonArray("graphs").get(0).getAsJsonObject().getAsJsonArray("nodes")).hasSize(GraphSlicer.MAX_NODES);
    assertThat(response.get("depth").getAsInt()).isZero();
    var cappedGraph = response.getAsJsonArray("graphs").get(0).getAsJsonObject();
    assertThat(cappedGraph.get("depth").getAsInt()).isZero();
    assertThat(cappedGraph.get("partialLevel").getAsInt()).isEqualTo(1);
    assertThat(cappedGraph.get("omittedNodes").getAsInt()).isEqualTo(1);
    assertThat(response.getAsJsonArray("drillDown")).isEmpty();
    assertThat(response.get("message").getAsString()).contains("Showing 150 of 151", "Omitted siblings", "may remain undiscoverable");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_an_unknown_component(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "component", "nope", "language", "java"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: No node 'nope' in the current architecture. "
        + "Call architecture_get with target current and without component to list the top-level node keys.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_explain_an_empty_response_without_assuming_the_cause(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    stubEmptyCurrent(harness);
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "depth" : 0,
        "requestedDepth" : 1,
        "capped" : false,
        "graphs" : [ ],
        "drillDown" : [ ],
        "message" : "No current architecture is visible for project 'my_project'. Check project access, organization, selected language and latest analysis."
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_relay_a_plan_or_permission_refusal(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java")
      .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json").withBody("{\"message\":\"Architecture is not available on your plan\"}")));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: SonarQube answered with a refusal to read the architecture of project 'my_project': "
        + "Architecture is not available on your plan. Check project permissions, feature availability and the public API gateway routing; HTTP 403 alone does not establish which check failed. "
        + "Architecture is available on SonarQube Cloud Team and Enterprise plans, and on SonarQube Server editions that include it. "
        + "Please verify your token has the required permissions for this operation.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_report_an_invalid_token(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(aResponse().withStatus(401)));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: SonarQube answered with Not authorized. Please check server credentials.. "
        + "Please verify your token is valid and has the correct permissions.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_reject_paging_and_depth_values_outside_their_integer_bounds(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    for (var arguments : List.of(
      Map.<String, Object>of("target", "current", "projectKey", "my_project", "depth", 0),
      Map.<String, Object>of("target", "current", "projectKey", "my_project", "depth", 65),
      Map.<String, Object>of("target", "current", "projectKey", "my_project", "depth", 1.5),
      Map.<String, Object>of("target", "directives", "projectKey", "my_project", "pageIndex", 0),
      Map.<String, Object>of("target", "findings", "projectKey", "my_project", "pageSize", 1001),
      Map.<String, Object>of("target", "findings", "projectKey", "my_project", "pageSize", -1))) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("input validation failed");
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_use_the_api_host_and_organization_id_on_sonarqube_cloud(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=" + CLOUD_ORGANIZATION_ID
      + "&branchId=" + MAIN_BRANCH_ID + "&language=java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    assertThat(result.isError()).isFalse();
    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs")).hasSize(1);
  }

  @SonarQubeMcpServerTest
  void it_should_surface_an_unavailable_public_endpoint(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).doesNotContain("No current architecture");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_an_incomplete_public_graph_response(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson("{}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("Invalid public architecture response", "The Architecture API returned an unexpected response.");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_malformed_current_graphs_instead_of_reporting_an_empty_architecture(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var client = harness.newClient(CLOUD_ENV);
    for (var graph : List.of(
      "[{\"nodeCount\":3,\"edgeCount\":0,\"nodes\":[]}]",
      "[{\"nodeCount\":1,\"edgeCount\":1,\"nodes\":[{\"id\":0,\"name\":\"a\",\"kind\":\"class\",\"edges\":[{\"toId\":0,\"weight\":-1}]}]}]",
      "[{\"nodeCount\":2,\"edgeCount\":0,\"nodes\":[{\"id\":1,\"name\":\"a\",\"kind\":\"class\",\"edges\":[]},{\"id\":1,\"name\":\"b\",\"kind\":\"class\",\"edges\":[]}]}]",
      "[{\"nodeCount\":2,\"edgeCount\":0,\"nodes\":[{\"id\":1,\"name\":\"a\",\"kind\":\"class\",\"edges\":[]},{\"id\":2,\"name\":\"a\",\"kind\":\"class\",\"edges\":[]}]}]",
      "[{\"nodeCount\":1,\"edgeCount\":1,\"nodes\":[{\"id\":1,\"name\":\"a\",\"kind\":\"class\",\"edges\":[{\"toId\":9,\"weight\":1}]}]}]",
      "[{\"nodeCount\":2,\"edgeCount\":0,\"nodes\":[{\"id\":1,\"name\":\"dir\",\"kind\":\"file\",\"edges\":[],\"nodes\":[{\"id\":2,\"name\":\"child\",\"kind\":\"file\",\"edges\":[]}]}]}]")) {
      harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + "java").willReturn(okJson(currentArchitectures(graph))));
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java"));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("The Architecture API returned an unexpected response.").doesNotContain("No current architecture is visible");
    }
  }

  private static void stubEmptyCurrent(SonarQubeMcpServerTestHarness harness) {
    for (var language : ArchitectureApi.LANGUAGES) {
      harness.getMockSonarQubeServer().stubFor(get(CLOUD_CURRENT + language).willReturn(okJson(currentArchitectures("[]"))));
    }
  }

}
