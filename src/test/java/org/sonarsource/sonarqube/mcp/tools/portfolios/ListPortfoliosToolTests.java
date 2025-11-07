/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.portfolios;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;
import org.sonarsource.sonarqube.mcp.serverapi.views.ViewsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListPortfoliosToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 404 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/portfolios?favorite=true", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?favorite=true").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/portfolios?favorite=true", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_portfolios(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?favorite=true")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyCloudResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertResultEquals(result, """
        {
          "portfolios" : [ ],
          "paging" : {
            "pageIndex" : 0,
            "pageSize" : 50,
            "total" : 0
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_portfolios_with_default_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?favorite=true")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateCloudResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertResultEquals(result, """
        {
          "portfolios" : [ {
            "id" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "name" : "Really important portfolio",
            "description" : "A helpful description of this portfolio",
            "enterpriseId" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "selection" : "projects",
            "isDraft" : false,
            "draftStage" : 0,
            "tags" : [ "front-end" ]
          }, {
            "id" : "f3bb5e4e-2654-5667-bfef-556fbc63568e",
            "name" : "Analytics Dashboard",
            "description" : "Dashboard for analytics data",
            "enterpriseId" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "selection" : "projects",
            "isDraft" : true,
            "draftStage" : 1,
            "tags" : [ "analytics", "backend" ]
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 50,
            "total" : 2
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_portfolios_with_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?enterpriseId=enterprise123&q=important&favorite=true&pageIndex=2&pageSize=10")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateCloudResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(
          ListPortfoliosTool.ENTERPRISE_ID_PROPERTY, "enterprise123",
          ListPortfoliosTool.QUERY_PROPERTY, "important",
          ListPortfoliosTool.FAVORITE_PROPERTY, "true",
          ListPortfoliosTool.PAGE_INDEX_PROPERTY, 2,
          ListPortfoliosTool.PAGE_SIZE_PROPERTY, 10));

      assertResultEquals(result, """
        {
          "portfolios" : [ {
            "id" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "name" : "Really important portfolio",
            "description" : "A helpful description of this portfolio",
            "enterpriseId" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "selection" : "projects",
            "isDraft" : false,
            "draftStage" : 0,
            "tags" : [ "front-end" ]
          }, {
            "id" : "f3bb5e4e-2654-5667-bfef-556fbc63568e",
            "name" : "Analytics Dashboard",
            "description" : "Dashboard for analytics data",
            "enterpriseId" : "2eaa4b2d-1543-4556-aede-445eab52457d",
            "selection" : "projects",
            "isDraft" : true,
            "draftStage" : 1,
            "tags" : [ "analytics", "backend" ]
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 50,
            "total" : 2
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fail_when_neither_enterpriseId_nor_favorite_is_provided(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(ListPortfoliosTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Either 'enterpriseId' must be provided or 'favorite' must be true", true));
    }

    @SonarQubeMcpServerTest
    void it_should_fail_when_both_favorite_and_draft_are_true(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(
          ListPortfoliosTool.ENTERPRISE_ID_PROPERTY, "enterprise123",
          ListPortfoliosTool.FAVORITE_PROPERTY, "true",
          ListPortfoliosTool.DRAFT_PROPERTY, "true"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Parameters 'favorite' and 'draft' cannot both be true at the same time", true));
    }

    @SonarQubeMcpServerTest
    void it_should_succeed_when_only_favorite_is_true_without_enterpriseId(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?favorite=true")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyCloudResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertResultEquals(result, """
        {
          "portfolios" : [ ],
          "paging" : {
            "pageIndex" : 0,
            "pageSize" : 50,
            "total" : 0
          }
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ViewsApi.VIEWS_SEARCH_PATH + "?qualifiers=VW").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListPortfoliosTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_portfolios(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ViewsApi.VIEWS_SEARCH_PATH + "?qualifiers=VW")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyServerResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListPortfoliosTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "portfolios" : [ ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 0
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_portfolios_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ViewsApi.VIEWS_SEARCH_PATH + "?qualifiers=VW")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateServerResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListPortfoliosTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "portfolios" : [ {
            "key" : "apache-jakarta-commons",
            "name" : "Apache Jakarta Commons",
            "qualifier" : "VW",
            "visibility" : "public",
            "isFavorite" : true
          }, {
            "key" : "Languages",
            "name" : "Languages",
            "qualifier" : "VW",
            "visibility" : "private",
            "isFavorite" : false
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 2
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_use_supported_parameters_on_server(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ViewsApi.VIEWS_SEARCH_PATH + "?q=apache&onlyFavorites=true&qualifiers=VW")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateServerResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ListPortfoliosTool.TOOL_NAME,
        Map.of(
          ListPortfoliosTool.QUERY_PROPERTY, "apache",
          ListPortfoliosTool.FAVORITE_PROPERTY, "true"));

      assertResultEquals(result, """
        {
          "portfolios" : [ {
            "key" : "apache-jakarta-commons",
            "name" : "Apache Jakarta Commons",
            "qualifier" : "VW",
            "visibility" : "public",
            "isFavorite" : true
          }, {
            "key" : "Languages",
            "name" : "Languages",
            "qualifier" : "VW",
            "visibility" : "private",
            "isFavorite" : false
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 2
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateEmptyCloudResponse() {
    return """
      {
        "portfolios": [],
        "page": {
          "pageIndex": 0,
          "pageSize": 50,
          "total": 0
        }
      }""";
  }

  private static String generateCloudResponse() {
    return """
      {
        "portfolios": [
          {
            "id": "2eaa4b2d-1543-4556-aede-445eab52457d",
            "enterpriseId": "2eaa4b2d-1543-4556-aede-445eab52457d",
            "name": "Really important portfolio",
            "description": "A helpful description of this portfolio",
            "selection": "projects",
            "favoriteId": "2eaa4b2d-1543-4556-aede-445eab52457d",
            "tags": [
              "front-end"
            ],
            "projects": [
              {
                "branchId": "2eaa4b2d-1543-4556-aede-445eab52457d",
                "id": "2eaa4b2d-1543-4556-aede-445eab52457d"
              }
            ],
            "isDraft": false,
            "draftStage": 0
          },
          {
            "id": "f3bb5e4e-2654-5667-bfef-556fbc63568e",
            "enterpriseId": "2eaa4b2d-1543-4556-aede-445eab52457d",
            "name": "Analytics Dashboard",
            "description": "Dashboard for analytics data",
            "selection": "projects",
            "tags": [
              "analytics",
              "backend"
            ],
            "projects": [
              {
                "branchId": "f3bb5e4e-2654-5667-bfef-556fbc63568e",
                "id": "f3bb5e4e-2654-5667-bfef-556fbc63568e"
              }
            ],
            "isDraft": true,
            "draftStage": 1
          }
        ],
        "page": {
          "pageIndex": 1,
          "pageSize": 50,
          "total": 2
        }
      }""";
  }

  private static String generateEmptyServerResponse() {
    return """
      {
        "paging": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 0
        },
        "components": []
      }""";
  }

  private static String generateServerResponse() {
    return """
      {
        "paging": {
          "pageIndex": 1,
          "pageSize": 100,
          "total": 2
        },
        "components": [
          {
            "key": "apache-jakarta-commons",
            "name": "Apache Jakarta Commons",
            "qualifier": "VW",
            "visibility": "public",
            "isFavorite": true
          },
          {
            "key": "Languages",
            "name": "Languages",
            "qualifier": "VW",
            "visibility": "private",
            "isFavorite": false
          }
        ]
      }""";
  }
}
