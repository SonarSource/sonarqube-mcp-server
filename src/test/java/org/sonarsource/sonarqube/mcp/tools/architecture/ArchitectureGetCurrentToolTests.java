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

  private static final String SERVER_CURRENT = "/api/v2" + ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=" + ArchitectureApi.SERVER_ORGANIZATION_ID
    + "&branchId=" + MAIN_BRANCH_ID + "&language=";

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    var tool = harness.newClient().listTools().stream().filter(t -> t.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.outputSchema()).isNull();
    assertThat(tool.inputSchema()).containsEntry("required", List.of("target", "projectKey"));
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Map<String, Object>>) tool.inputSchema().get("properties");
    assertThat(properties).containsOnlyKeys("target", "projectKey", "component", "depth", "language", "directiveId", "type", "category", "pageIndex", "pageSize");
    assertThat(properties.get("target")).containsEntry("enum", List.of("current", "intended", "directives", "findings"));
    assertThat(properties.get("language")).containsEntry("enum", List.of("java", "cs", "js", "ts", "py"));
  }

  @SonarQubeMcpServerTest
  void it_should_return_the_top_level_with_rolled_up_edges_and_query_every_language(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    stubEmptyCurrent(harness);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient();

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
            { "key" : "app", "kind" : "module", "children" : 2 },
            { "key" : "app:com.acme.web", "kind" : "package", "children" : 1 },
            { "key" : "app:com.acme.domain", "kind" : "package", "children" : 2 }
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
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/api/v2/architecture/current-architectures")).isEqualTo(5);
  }

  @SonarQubeMcpServerTest
  void it_should_drill_down_into_a_component(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient();

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
            { "key" : "app:com.acme.domain.Service", "kind" : "class", "children" : 0 },
            { "key" : "app:com.acme.domain.Repository", "kind" : "class", "children" : 0 }
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
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/api/v2/architecture/current-architectures")).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_report_feedback_pairs_and_the_weight_to_nodes_outside_the_view(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures("""
      [ { "nodeCount": 4, "edgeCount": 3, "nodes": [
        { "id": 1, "name": "app", "kind": "module", "edges": [], "nodes": [
          { "id": 2, "name": "web", "kind": "package", "edges": [ { "toId": 3, "weight": 3 } ] },
          { "id": 3, "name": "domain", "kind": "package", "edges": [ { "toId": 2, "weight": 1 }, { "toId": 4, "weight": 5 } ] }
        ] },
        { "id": 4, "name": "lib", "kind": "module", "edges": [] }
      ] } ]"""))));
    var client = harness.newClient();

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
            { "key" : "app:web", "kind" : "package", "children" : 0 },
            { "key" : "app:domain", "kind" : "package", "children" : 0 }
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
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(cyclicJavaGraph()))));
    var client = harness.newClient();

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
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "ts").willReturn(okJson(currentArchitectures("""
      [ { "nodeCount": 2, "edgeCount": 0, "nodes": [
        { "id": 1, "name": "src", "kind": "directory", "edges": [], "nodes": [ { "id": 2, "name": "index.ts", "kind": "file", "edges": [] } ] }
      ] } ]"""))));
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "ts", "depth", 2));

    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs").get(0).getAsJsonObject()
      .getAsJsonArray("nodes").get(1).getAsJsonObject().get("key").getAsString()).isEqualTo("src/index.ts");
  }

  @SonarQubeMcpServerTest
  void it_should_prefer_the_separator_of_the_node_over_its_kind(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "ts").willReturn(okJson(currentArchitectures("""
      [ { "id": "graph-ts", "nodeCount": 2, "edgeCount": 0, "nodes": [
        { "id": 1, "name": "web", "kind": "module", "separator": "/", "edges": [], "nodes": [
          { "id": 2, "name": "app.ts", "kind": "file", "separator": "/", "edges": [] } ] }
      ] } ]"""))));
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "ts", "depth", 2));

    assertThat(JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("graphs").get(0).getAsJsonObject()
      .getAsJsonArray("nodes").get(1).getAsJsonObject().get("key").getAsString()).isEqualTo("web/app.ts");
  }

  @SonarQubeMcpServerTest
  void it_should_cap_a_large_graph_and_suggest_drill_downs(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var largeGraph = largeJavaGraph(1000);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(largeGraph))));
    var client = harness.newClient();

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
      .mapToObj(id -> "{\"id\":" + id + ",\"name\":\"root" + id + "\",\"kind\":\"module\"}")
      .collect(java.util.stream.Collectors.joining(","));
    var graph = "[{\"nodeCount\":151,\"edgeCount\":0,\"nodes\":[" + nodes + "]}]";
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(graph))));

    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME,
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
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH))));
    var client = harness.newClient();

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
    var client = harness.newClient();

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
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java")
      .willReturn(aResponse().withStatus(403).withHeader("Content-Type", "application/json").withBody("{\"message\":\"Architecture is not available on your plan\"}")));
    var client = harness.newClient();

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
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(aResponse().withStatus(401)));
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "language", "java"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: SonarQube answered with Not authorized. Please check server credentials.. "
        + "Please verify your token is valid and has the correct permissions.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_reject_a_depth_below_one(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "depth", 0));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: depth must be 1 or more.").build());
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
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).doesNotContain("No current architecture");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_an_incomplete_public_graph_response(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + "java").willReturn(okJson("{}")));
    var result = harness.newClient().callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "current", "projectKey", "my_project", "language", "java"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("No complete graph was read");
  }

  private static void stubEmptyCurrent(SonarQubeMcpServerTestHarness harness) {
    for (var language : ArchitectureApi.LANGUAGES) {
      harness.getMockSonarQubeServer().stubFor(get(SERVER_CURRENT + language).willReturn(okJson(currentArchitectures("[]"))));
    }
  }

}
