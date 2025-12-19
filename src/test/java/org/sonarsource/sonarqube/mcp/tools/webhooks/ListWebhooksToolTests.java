/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.webhooks;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.WebhooksApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ListWebhooksToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListWebhooksTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "webhooks":{
               "description":"List of configured webhooks",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "hasSecret":{
                        "type":"boolean",
                        "description":"Whether the webhook has a configured secret"
                     },
                     "key":{
                        "type":"string",
                        "description":"Webhook unique key"
                     },
                     "name":{
                        "type":"string",
                        "description":"Webhook display name"
                     },
                     "url":{
                        "type":"string",
                        "description":"Target URL for the webhook"
                     }
                  },
                  "required":[
                     "hasSecret",
                     "key",
                     "name",
                     "url"
                  ]
               }
            }
         },
         "required":[
            "webhooks"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?organization=org").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyWebhooksResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "webhooks" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_list_organization_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhooksResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "webhooks" : [ {
            "key" : "UUID-1",
            "name" : "my first webhook",
            "url" : "http://www.my-webhook-listener.com/sonarqube",
            "hasSecret" : false
          }, {
            "key" : "UUID-2",
            "name" : "my 2nd webhook",
            "url" : "https://www.my-other-webhook-listener.com/fancy-listner",
            "hasSecret" : true
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_list_project_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?organization=org&project=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSingleWebhookResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListWebhooksTool.TOOL_NAME,
        Map.of(ListWebhooksTool.PROJECT_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "webhooks" : [ {
            "key" : "UUID-1",
            "name" : "project webhook",
            "url" : "http://project.webhook.com/endpoint",
            "hasSecret" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_project_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?organization=org&project=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyWebhooksResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListWebhooksTool.TOOL_NAME,
        Map.of(ListWebhooksTool.PROJECT_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "webhooks" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEmptyWebhooksResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "webhooks" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_list_global_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhooksResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "webhooks" : [ {
            "key" : "UUID-1",
            "name" : "my first webhook",
            "url" : "http://www.my-webhook-listener.com/sonarqube",
            "hasSecret" : false
          }, {
            "key" : "UUID-2",
            "name" : "my 2nd webhook",
            "url" : "https://www.my-other-webhook-listener.com/fancy-listner",
            "hasSecret" : true
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_list_project_webhooks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH + "?project=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSingleWebhookResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ListWebhooksTool.TOOL_NAME,
        Map.of(ListWebhooksTool.PROJECT_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "webhooks" : [ {
            "key" : "UUID-1",
            "name" : "project webhook",
            "url" : "http://project.webhook.com/endpoint",
            "hasSecret" : false
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_server_error_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(WebhooksApi.LIST_PATH).willReturn(aResponse().withStatus(500)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListWebhooksTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/webhooks/list").build());
    }
  }

  private static String generateEmptyWebhooksResponse() {
    return """
      {
        "webhooks": []
      }
      """;
  }

  private static String generateWebhooksResponse() {
    return """
      {
        "webhooks": [
          {
            "key": "UUID-1",
            "name": "my first webhook",
            "url": "http://www.my-webhook-listener.com/sonarqube",
            "hasSecret": false
          },
          {
            "key": "UUID-2",
            "name": "my 2nd webhook",
            "url": "https://www.my-other-webhook-listener.com/fancy-listner",
            "hasSecret": true
          }
        ]
      }
      """;
  }

  private static String generateSingleWebhookResponse() {
    return """
      {
        "webhooks": [
          {
            "key": "UUID-1",
            "name": "project webhook",
            "url": "http://project.webhook.com/endpoint",
            "hasSecret": false
          }
        ]
      }
      """;
  }

}
