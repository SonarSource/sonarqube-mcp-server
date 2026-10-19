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
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;

class ArchitectureGetIntendedToolTests {

  private static final String CLOUD_INTENDED = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID;

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
        "models" : [ ],
        "message" : "No intended architecture is defined for project 'my_project' in cs."
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_explain_the_public_project_id_gap_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("no supported public API resolves it", "Nothing was read or changed");
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

  private static void assertViewEquals(McpSchema.CallToolResult result, String expected) {
    var json = com.google.gson.JsonParser.parseString(((McpSchema.TextContent) result.content().getFirst()).text()).getAsJsonObject();
    assertThat(json.remove("revision").getAsString()).isNotBlank();
    json.getAsJsonArray("models").forEach(model -> model.getAsJsonObject().remove("partial"));
    assertThat(json).isEqualTo(com.google.gson.JsonParser.parseString(expected));
  }

}
