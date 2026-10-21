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

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureGetIntendedToolTests {

  private static final String CLOUD_INTENDED = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID;

  @SonarQubeMcpServerTest
  void it_should_reject_incomplete_public_models_instead_of_exporting_partial_data(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var client = harness.newClient(CLOUD_ENV);
    for (var model : new String[] {
      "null",
      "{\"language\":\"java\",\"groups\":[],\"constraints\":[]}",
      "{\"type\":\"namespace\",\"groups\":[],\"constraints\":[]}",
      "{\"type\":\"namespace\",\"language\":\"java\",\"constraints\":[]}",
      "{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[]}",
      "{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[{}],\"constraints\":[]}",
      "{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[],\"constraints\":[{\"from\":\"A\"}]}"
    }) {
      harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED)
        .willReturn(okJson("{\"id\":\"11111111-1111-4111-8111-111111111111\",\"intendedArchitectures\":[" + model.replace("\"type\"", "\"qualifiers\"") + "]}")));

      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));

      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("incomplete intended architecture", "No complete model was read");
    }
  }

  @SonarQubeMcpServerTest
  void it_should_keep_constraints_touching_selected_branch_and_leaf_roots(SonarQubeMcpServerTestHarness harness) {
    var model = """
      [{"type":"namespace","language":"java","groups":[{"label":"Domain","groups":[{"label":"Billing"}]},{"label":"Web"},{"label":"DomainExtra"}],
      "constraints":[{"from":"Web","to":"Domain"},{"from":"Domain","to":"Web"},
      {"from":"Web","to":"Domain/Billing"},{"from":"Domain/Billing","to":"Web"},
      {"from":"Web","to":"DomainExtra"},{"from":"DomainExtra","to":"Web"}]}]""";
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(model))));
    var client = harness.newClient(CLOUD_ENV);
    var constraints = JsonParser.parseString(model).getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("constraints");

    for (var component : new String[] {"Domain", "Domain/Billing"}) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "component", component));
      assertThat(result.isError()).isFalse();
      var view = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonArray("models").get(0).getAsJsonObject();
      var expected = new JsonArray();
      if (component.equals("Domain")) {
        expected.add(constraints.get(0));
        expected.add(constraints.get(1));
      }
      expected.add(constraints.get(2));
      expected.add(constraints.get(3));
      assertThat(view.getAsJsonArray("constraints")).isEqualTo(expected);
      if (component.equals("Domain/Billing")) {
        assertThat(view.getAsJsonArray("groups")).isEmpty();
      }
    }
  }

  @SonarQubeMcpServerTest
  void it_should_return_the_whole_model_on_sonarqube_cloud(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));

    assertViewEquals(result, """
      {
        "projectKey" : "my_project",
        "models" : [ {
          "language" : "java",
          "type" : "namespace",
          "groups" : [ { "label" : "app", "patterns" : [ "app:**" ], "groups" : [
            { "label" : "web", "patterns" : [ "app:com.acme.web.**" ] },
            { "label" : "domain", "patterns" : [ "app:com.acme.domain.**" ], "interface" : true }
          ] } ],
          "constraints" : [ { "from" : "app/web", "to" : "app/domain" } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_limit_the_depth_and_count_hidden_subgroups(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "depth", 1));

    assertViewEquals(result, """
      {
        "projectKey" : "my_project",
        "depth" : 1,
        "partial" : true,
        "models" : [ {
          "language" : "java",
          "type" : "namespace",
          "groups" : [ { "label" : "app", "patterns" : [ "app:**" ], "hiddenSubgroups" : 2 } ],
          "constraints" : [ { "from" : "app/web", "to" : "app/domain" } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_start_from_a_group(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "component", "app"));

    assertViewEquals(result, """
      {
        "projectKey" : "my_project",
        "component" : "app",
        "partial" : true,
        "models" : [ {
          "language" : "java",
          "type" : "namespace",
          "groups" : [
            { "label" : "web", "patterns" : [ "app:com.acme.web.**" ] },
            { "label" : "domain", "patterns" : [ "app:com.acme.domain.**" ], "interface" : true }
          ],
          "constraints" : [ { "from" : "app/web", "to" : "app/domain" } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_an_unknown_group(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "component", "app/nope"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true)
      .addTextContent("An error occurred during the tool execution: No group 'app/nope' in the intended architecture. "
        + "Call architecture_get with target intended and without component to list the group keys.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_say_when_no_model_exists_for_the_language(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "language", "cs"));

    assertViewEquals(result, """
      {
        "projectKey" : "my_project",
        "partial" : true,
        "models" : [ ],
        "message" : "No intended architecture is visible for project 'my_project' in cs. It may be undefined, inaccessible or in another organization."
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_reject_unavailable_server_intended_target_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("current").doesNotContain("No intended architecture is visible");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/components/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_relay_a_permission_refusal(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Insufficient privileges\"}")));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));

    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
      .startsWith("An error occurred during the tool execution: SonarQube answered with a refusal to read the architecture of project 'my_project': "
        + "Insufficient privileges. Check project permissions, feature availability and the public API gateway routing; HTTP 403 alone does not establish which check failed.");
  }

  @SonarQubeMcpServerTest
  void it_should_key_the_model_by_main_branch_id_on_sonarqube_cloud(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var client = harness.newClient(CLOUD_ENV);

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "depth", 1));

    assertThat(result.isError()).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/components/search_projects")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_an_incomplete_public_response(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson("{}")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("No complete model was read");
  }

  @SonarQubeMcpServerTest
  void it_should_treat_component_as_an_exact_key_not_a_wildcard(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "component", "app/*"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No group 'app/*'");
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_ambiguous_component_keys_without_losing_groups_in_the_full_read(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var models = JsonParser.parseString("""
      [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[
        {"label":"UI","groups":[{"label":"API","patterns":["nested:**"]}]},
        {"label":"UI/API","patterns":["literal:**"]}],"constraints":[]}]
      """).getAsJsonArray();
    var groups = models.get(0).getAsJsonObject().getAsJsonArray("groups");
    var client = harness.newClient(CLOUD_ENV);
    for (var order = 0; order < 2; order++) {
      harness.getMockSonarQubeServer().stubFor(get(CLOUD_INTENDED).willReturn(okJson(intendedArchitectures(models.toString()))));
      var full = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
      assertThat(full.isError()).isFalse();
      assertThat(JsonParser.parseString(textOf(full)).getAsJsonObject().getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("groups"))
        .isEqualTo(groups);
      var partial = client.callTool(ArchitectureGetTool.TOOL_NAME,
        Map.of("target", "intended", "projectKey", "my_project", "component", "UI/API"));
      assertThat(partial.isError()).isTrue();
      assertThat(textOf(partial)).contains("matches multiple groups", "Read the complete intended model without component");
      groups.add(groups.remove(0));
    }
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", "/architecture/")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", "/architecture/")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("DELETE", "/architecture/")).isEmpty();
  }

  private static void assertViewEquals(McpSchema.CallToolResult result, String expected) {
    var json = com.google.gson.JsonParser.parseString(((McpSchema.TextContent) result.content().getFirst()).text()).getAsJsonObject();
    assertThat(json.remove("revision").getAsString()).isNotBlank();
    json.getAsJsonArray("models").forEach(model -> model.getAsJsonObject().remove("partial"));
    assertThat(json).isEqualTo(com.google.gson.JsonParser.parseString(expected));
  }

}
