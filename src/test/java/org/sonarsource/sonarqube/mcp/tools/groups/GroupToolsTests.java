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
package org.sonarsource.sonarqube.mcp.tools.groups;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.users.GroupsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class GroupToolsTests {
  private static final String ORGANIZATION_ID = "b2c3d4e5-f6a7-4901-bcde-f12345678901";
  private static final String GROUP_ID = "a1b2c3d4-e5f6-4890-abcd-ef1234567890";
  private static final Map<String, String> CLOUD = Map.of("SONARQUBE_ORG", "org");
  private static final String GROUP_JSON = """
    {"id":"a1b2c3d4-e5f6-4890-abcd-ef1234567890","organizationId":"b2c3d4e5-f6a7-4901-bcde-f12345678901",
     "name":"OAuth fixture","description":"Empty test group","builtIn":false,"managed":false}
    """;

  @SonarQubeMcpServerTest
  void it_should_list_groups_and_return_paging(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(GroupsApi.GROUPS_PATH + "?organizationIds=" + ORGANIZATION_ID + "&name=OAuth+fixture")
      .willReturn(okJson("{\"groups\":[" + GROUP_JSON + "],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":1}}")));
    var client = harness.newClient(CLOUD);
    var result = client.callTool(ListGroupsTool.TOOL_NAME, Map.of("organizationId", ORGANIZATION_ID, "name", "OAuth fixture"));
    assertResultEquals(result, "{\"groups\":[" + GROUP_JSON + "],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":1}}");
  }

  @SonarQubeMcpServerTest
  void it_should_create_an_empty_group_with_normal_api_permissions(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(post(GroupsApi.GROUPS_PATH).willReturn(aResponse().withStatus(201).withBody(GROUP_JSON)));
    var result = harness.newClient(CLOUD).callTool(CreateGroupTool.TOOL_NAME,
      Map.of("organizationId", ORGANIZATION_ID, "name", "OAuth fixture", "description", "Empty test group"));
    assertResultEquals(result, "{\"group\":" + GROUP_JSON + "}");
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new org.sonarsource.sonarqube.mcp.harness.ReceivedRequest(
      "Bearer token", "{\"organizationId\":\"" + ORGANIZATION_ID + "\",\"name\":\"OAuth fixture\",\"description\":\"Empty test group\"}"));
  }

  @SonarQubeMcpServerTest
  void it_should_delete_only_the_recorded_group_id(SonarQubeMcpServerTestHarness harness) {
    var path = GroupsApi.GROUPS_PATH + "/" + GROUP_ID;
    harness.getMockSonarQubeServer().stubFor(delete(path).willReturn(aResponse().withStatus(204)));
    var result = harness.newClient(CLOUD).callTool(DeleteGroupTool.TOOL_NAME, Map.of("groupId", GROUP_ID));
    assertResultEquals(result, "{\"groupId\":\"" + GROUP_ID + "\",\"success\":true}");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(path)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_return_existing_cloud_permission_errors(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(post(GroupsApi.GROUPS_PATH)
      .willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Organization administrator permission required\"}")));
    var result = harness.newClient(CLOUD).callTool(CreateGroupTool.TOOL_NAME, Map.of("organizationId", ORGANIZATION_ID, "name", "fixture"));
    assertThat(result.isError()).isTrue();
    assertThat(result.toString()).contains("Organization administrator permission required");
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations_and_extend_cloud_system_tools(SonarQubeMcpServerTestHarness harness) {
    var tools = harness.newClient(CLOUD).listTools();
    var list = tools.stream().filter(tool -> ListGroupsTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow();
    var create = tools.stream().filter(tool -> CreateGroupTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow();
    var delete = tools.stream().filter(tool -> DeleteGroupTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow();
    assertThat(list.annotations().readOnlyHint()).isTrue();
    assertThat(list.annotations().destructiveHint()).isFalse();
    assertThat(create.annotations().readOnlyHint()).isFalse();
    assertThat(create.annotations().destructiveHint()).isFalse();
    assertThat(delete.annotations().readOnlyHint()).isFalse();
    assertThat(delete.annotations().destructiveHint()).isTrue();
    assertThat(delete.annotations().idempotentHint()).isTrue();
    for (var tool : java.util.List.of(list, create, delete)) {
      assertThat(tool.annotations().openWorldHint()).isTrue();
      assertThat(tool.outputSchema()).isNull();
    }
    assertThat(tools).noneMatch(tool -> "get_system_health".equals(tool.name()));
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_server_only_system_tools_and_exclude_cloud_groups(SonarQubeMcpServerTestHarness harness) {
    var tools = harness.newClient().listTools();
    assertThat(tools).anyMatch(tool -> "get_system_health".equals(tool.name()));
    assertThat(tools).noneMatch(tool -> java.util.Set.of(ListGroupsTool.TOOL_NAME, CreateGroupTool.TOOL_NAME, DeleteGroupTool.TOOL_NAME).contains(tool.name()));
  }

  @SonarQubeMcpServerTest
  void it_should_keep_admin_tools_opt_in(SonarQubeMcpServerTestHarness harness) {
    var tools = harness.newClient(Map.of("SONARQUBE_ORG", "org", "SONARQUBE_TOOLSETS", "projects")).listTools();
    assertThat(tools).noneMatch(tool -> java.util.Set.of(ListGroupsTool.TOOL_NAME, CreateGroupTool.TOOL_NAME, DeleteGroupTool.TOOL_NAME).contains(tool.name()));
  }

  @SonarQubeMcpServerTest
  void it_should_hide_group_mutations_in_read_only_mode(SonarQubeMcpServerTestHarness harness) {
    var tools = harness.newClient(Map.of("SONARQUBE_ORG", "org", "SONARQUBE_READ_ONLY", "true")).listTools();
    assertThat(tools).anyMatch(tool -> ListGroupsTool.TOOL_NAME.equals(tool.name()));
    assertThat(tools).noneMatch(tool -> java.util.Set.of(CreateGroupTool.TOOL_NAME, DeleteGroupTool.TOOL_NAME).contains(tool.name()));
  }

  @SonarQubeMcpServerTest
  void it_should_return_server_and_managed_group_errors(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(GroupsApi.GROUPS_PATH)).willReturn(aResponse().withStatus(500)));
    assertThat(client.callTool(ListGroupsTool.TOOL_NAME, Map.of("organizationId", ORGANIZATION_ID)).isError()).isTrue();
    harness.getMockSonarQubeServer().stubFor(delete(GroupsApi.GROUPS_PATH + "/" + GROUP_ID)
      .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"Managed group can not be modified\"}")));
    var deletion = client.callTool(DeleteGroupTool.TOOL_NAME, Map.of("groupId", GROUP_ID));
    assertThat(deletion.isError()).isTrue();
    assertThat(deletion.toString()).contains("Managed group can not be modified");
  }
}

