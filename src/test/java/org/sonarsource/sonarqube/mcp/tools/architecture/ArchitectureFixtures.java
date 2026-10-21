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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;

/**
 * Synthetic architecture data shared by the architecture tool tests.
 */
final class ArchitectureFixtures {

  static final Map<String, String> CLOUD_ENV = Map.of("SONARQUBE_ORG", "org");
  /** Org uuid returned by the harness's organizations stub. */
  static final String CLOUD_ORGANIZATION_ID = "00000000-0000-0000-0000-000000000001";
  static final String MAIN_BRANCH_ID = "main-branch-id";

  /**
   * app (module) contains com.acme.web (Controller) and com.acme.domain (Service, Repository).
   */
  static final String SMALL_JAVA_GRAPH = """
    [ { "nodeCount": 6, "edgeCount": 3, "nodes": [
      { "id": 1, "name": "app", "kind": "module", "edges": [], "nodes": [
        { "id": 2, "name": "com.acme.web", "kind": "package", "edges": [], "nodes": [
          { "id": 4, "name": "Controller", "kind": "class", "edges": [ { "toId": 5, "weight": 3 }, { "toId": 6, "weight": 1 } ] }
        ] },
        { "id": 3, "name": "com.acme.domain", "kind": "package", "edges": [], "nodes": [
          { "id": 5, "name": "Service", "kind": "class", "edges": [ { "toId": 6, "weight": 2 } ] },
          { "id": 6, "name": "Repository", "kind": "class", "edges": [] }
        ] }
      ] }
    ] } ]""";

  static final String INTENDED_JAVA_MODEL = """
    [ { "id": "11111111-1111-4111-8111-111111111111", "type": "namespace", "language": "java",
        "groups": [ { "label": "app", "patterns": [ "app:**" ], "groups": [
          { "label": "web", "patterns": [ "app:com.acme.web.**" ] },
          { "label": "domain", "patterns": [ "app:com.acme.domain.**" ], "interface": true }
        ] } ],
        "constraints": [ { "from": "app/web", "to": "app/domain" } ] } ]""";

  private ArchitectureFixtures() {
  }

  /** Wraps graphs the way {@code GET /architecture/current-architectures} returns them. */
  static String currentArchitectures(String graphs) {
    return "{ \"currentArchitectures\": " + graphs + " }";
  }

  /** Wraps models the way {@code GET /architecture/intended-architectures} returns them. */
  static String intendedArchitectures(String models) {
    var array = com.google.gson.JsonParser.parseString(models).getAsJsonArray().deepCopy();
    var response = new com.google.gson.JsonObject();
    for (var element : array) {
      var model = element.getAsJsonObject();
      if (model.has("id")) {
        response.add("id", model.remove("id"));
      }
      model.add("qualifiers", model.remove("type"));
    }
    if (!array.isEmpty() && !response.has("id")) {
      response.addProperty("id", "11111111-1111-4111-8111-111111111111");
    }
    response.add("intendedArchitectures", array);
    return response.toString();
  }

  /**
   * One module with {@code packages} packages of one class each; package i depends on package i+1.
   */
  static String largeJavaGraph(int packages) {
    var packageNodes = IntStream.range(0, packages)
      .mapToObj(i -> {
        var packageId = 2 + 2 * i;
        var classId = packageId + 1;
        var edge = i + 1 < packages ? "{ \"toId\": " + (classId + 2) + ", \"weight\": " + (i + 1) + " }" : "";
        return "{ \"id\": " + packageId + ", \"name\": \"org.big.p" + i + "\", \"kind\": \"package\", \"edges\": [], \"nodes\": [ "
          + "{ \"id\": " + classId + ", \"name\": \"C" + i + "\", \"kind\": \"class\", \"edges\": [ " + edge + " ] } ] }";
      })
      .collect(Collectors.joining(", "));
    return "[ { \"nodeCount\": " + (1 + 2 * packages) + ", \"edgeCount\": " + (packages - 1) + ", \"nodes\": [ "
      + "{ \"id\": 1, \"name\": \"big\", \"kind\": \"module\", \"edges\": [], \"nodes\": [ " + packageNodes + " ] } ] } ]";
  }

  /**
   * Deterministic synthetic graph: six mutually dependent packages and one external module.
   * All names, dependency counts and weights are generated here, without analysis data from a real project.
   */
  static String cyclicJavaGraph() {
    var packages = IntStream.range(0, 6).mapToObj(i -> {
      var edges = IntStream.range(0, 6).filter(j -> j != i)
        .mapToObj(j -> "{ \"toId\": " + (3 + 2 * j) + ", \"weight\": " + (10 * (6 - i) + 6 - j) + " }")
        .collect(Collectors.joining(", "));
      if (i == 0) {
        edges += ", { \"toId\": 101, \"weight\": 17 }";
      }
      return "{ \"id\": " + (2 + 2 * i) + ", \"name\": \"p" + i + "\", \"kind\": \"package\", \"edges\": [], \"nodes\": [ "
        + "{ \"id\": " + (3 + 2 * i) + ", \"name\": \"Service\", \"kind\": \"class\", \"edges\": [ " + edges + " ] } ] }";
    }).collect(Collectors.joining(", "));
    return "[ { \"nodeCount\": 15, \"edgeCount\": 32, \"nodes\": [ "
      + "{ \"id\": 1, \"name\": \"core\", \"kind\": \"module\", \"edges\": [], \"nodes\": [ " + packages + " ] }, "
      + "{ \"id\": 100, \"name\": \"external\", \"kind\": \"module\", \"edges\": [], \"nodes\": [ "
      + "{ \"id\": 101, \"name\": \"Client\", \"kind\": \"class\", \"edges\": [ { \"toId\": 3, \"weight\": 5 } ] } ] } ] } ]";
  }

  static void stubMainBranch(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project").willReturn(okJson("""
      { "branches": [
        { "name": "feature", "isMain": false, "type": "SHORT", "branchId": "other-branch-id" },
        { "name": "main", "isMain": true, "type": "LONG", "branchId": "%s" }
      ] }""".formatted(MAIN_BRANCH_ID))));
  }

  static String textOf(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().getFirst()).text();
  }

}
