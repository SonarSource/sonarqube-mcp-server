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
package org.sonarsource.sonarqube.mcp.serverapi.users;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupsApiTest {
  private static final UUID ORGANIZATION_ID = UUID.fromString("b2c3d4e5-f6a7-4901-bcde-f12345678901");
  private static final UUID GROUP_ID = UUID.fromString("a1b2c3d4-e5f6-4890-abcd-ef1234567890");
  private static final String GROUP_JSON = """
    {"id":"a1b2c3d4-e5f6-4890-abcd-ef1234567890","organizationId":"b2c3d4e5-f6a7-4901-bcde-f12345678901",
     "name":"OAuth fixture","description":"Empty test group","builtIn":false,"managed":false}
    """;
  @RegisterExtension
  static WireMockExtension website = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();
  @RegisterExtension
  static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();
  private HttpClientProvider clients;
  private GroupsApi groups;

  @BeforeEach
  void setUp() {
    clients = new HttpClientProvider("Cloud group API tests");
    groups = new GroupsApi(new ServerApiHelper(new EndpointParams(website.baseUrl(), null, api.baseUrl(), true), clients.getHttpClient("token-b")));
  }

  @AfterEach
  void tearDown() { clients.shutdown(); }

  @Test
  void it_should_list_from_the_api_origin_with_exact_filters_and_pagination() {
    api.stubFor(get(urlPathEqualTo(GroupsApi.GROUPS_PATH)).willReturn(okJson("{\"groups\":[" + GROUP_JSON + "],\"page\":{\"pageIndex\":2,\"pageSize\":10,\"total\":11}}")));
    var response = groups.list(ORGANIZATION_ID, "OAuth fixture", 2, 10);
    assertThat(response.groups()).hasSize(1);
    assertThat(response.groups().getFirst().id()).isEqualTo(GROUP_ID.toString());
    assertThat(response.page()).isEqualTo(new GroupsApi.Page(2, 10, 11));
    api.verify(getRequestedFor(urlPathEqualTo(GroupsApi.GROUPS_PATH))
      .withHeader("Authorization", equalTo("Bearer token-b"))
      .withQueryParam("organizationIds", equalTo(ORGANIZATION_ID.toString()))
      .withQueryParam("name", equalTo("OAuth fixture")).withQueryParam("pageIndex", equalTo("2")).withQueryParam("pageSize", equalTo("10")));
    website.verify(0, getRequestedFor(urlPathEqualTo(GroupsApi.GROUPS_PATH)));
  }

  @Test
  void it_should_preserve_empty_lists_and_page_metadata() {
    api.stubFor(get(urlPathEqualTo(GroupsApi.GROUPS_PATH)).willReturn(okJson("{\"groups\":[],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}")));
    assertThat(groups.list(ORGANIZATION_ID, null, null, null).groups()).isEmpty();
    api.verify(getRequestedFor(urlEqualTo(GroupsApi.GROUPS_PATH + "?organizationIds=" + ORGANIZATION_ID)));
  }

  @Test
  void it_should_create_an_empty_group_without_membership_or_role_fields() {
    api.stubFor(post(GroupsApi.GROUPS_PATH).willReturn(aResponse().withStatus(201).withBody(GROUP_JSON)));
    var group = groups.create(ORGANIZATION_ID, "OAuth fixture", "Empty test group");
    assertThat(group.id()).isEqualTo(GROUP_ID.toString());
    assertThat(group.builtIn()).isFalse();
    assertThat(group.managed()).isFalse();
    api.verify(postRequestedFor(urlEqualTo(GroupsApi.GROUPS_PATH))
      .withHeader("Authorization", equalTo("Bearer token-b")).withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson("{\"organizationId\":\"" + ORGANIZATION_ID + "\",\"name\":\"OAuth fixture\",\"description\":\"Empty test group\"}")));
    website.verify(0, postRequestedFor(urlEqualTo(GroupsApi.GROUPS_PATH)));
  }

  @Test
  void it_should_omit_an_unset_description() {
    api.stubFor(post(GroupsApi.GROUPS_PATH).willReturn(aResponse().withStatus(201).withBody(GROUP_JSON)));
    groups.create(ORGANIZATION_ID, "OAuth fixture", null);
    api.verify(postRequestedFor(urlEqualTo(GroupsApi.GROUPS_PATH)).withRequestBody(equalToJson("{\"organizationId\":\"" + ORGANIZATION_ID + "\",\"name\":\"OAuth fixture\"}")));
  }

  @Test
  void it_should_delete_by_typed_group_id_on_the_api_origin() {
    var path = GroupsApi.GROUPS_PATH + "/" + GROUP_ID;
    api.stubFor(delete(path).willReturn(aResponse().withStatus(204)));
    groups.delete(GROUP_ID);
    groups.delete(GROUP_ID);
    api.verify(2, deleteRequestedFor(urlEqualTo(path)).withHeader("Authorization", equalTo("Bearer token-b")));
    website.verify(0, deleteRequestedFor(urlEqualTo(path)));
  }

  @Test
  void it_should_preserve_cloud_permissions_and_server_failures() {
    api.stubFor(get(urlPathEqualTo(GroupsApi.GROUPS_PATH)).willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Organization administrator permission required\"}")));
    assertThatThrownBy(() -> groups.list(ORGANIZATION_ID, null, null, null)).isInstanceOf(ForbiddenException.class)
      .hasMessageContaining("Organization administrator permission required");
    api.stubFor(post(GroupsApi.GROUPS_PATH).willReturn(aResponse().withStatus(401)));
    assertThatThrownBy(() -> groups.create(ORGANIZATION_ID, "fixture", null)).isInstanceOf(UnauthorizedException.class);
    api.stubFor(delete(GroupsApi.GROUPS_PATH + "/" + GROUP_ID).willReturn(aResponse().withStatus(500)));
    assertThatThrownBy(() -> groups.delete(GROUP_ID)).isInstanceOf(ServerInternalErrorException.class);
  }

  @Test
  void it_should_not_report_cleanup_success_for_an_unexpected_response() {
    api.stubFor(delete(GroupsApi.GROUPS_PATH + "/" + GROUP_ID).willReturn(okJson("{}")));
    assertThatThrownBy(() -> groups.delete(GROUP_ID)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no-content response");
  }
}

