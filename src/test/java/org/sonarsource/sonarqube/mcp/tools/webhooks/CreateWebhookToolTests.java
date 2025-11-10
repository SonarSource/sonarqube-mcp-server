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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class CreateWebhookToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(CreateWebhookTool.TOOL_NAME)).findFirst().orElseThrow();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "hasSecret":{
               "type":"boolean",
               "description":"Whether the webhook has a secret"
            },
            "key":{
               "type":"string",
               "description":"The created webhook key"
            },
            "name":{
               "type":"string",
               "description":"The webhook name"
            },
            "url":{
               "type":"string",
               "description":"The webhook URL"
            }
         },
         "required":[
            "hasSecret",
            "key",
            "name",
            "url"
         ]
      }
      """);
  }

  private static final String URL = "https://example.com/webhook";

  @Nested
  class MissingPrerequisites {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_name_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(CreateWebhookTool.URL_PROPERTY, URL));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: name", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_url_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(CreateWebhookTool.NAME_PROPERTY, "Test Webhook"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: url", true));
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH + "?organization=org").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Test Webhook",
          CreateWebhookTool.URL_PROPERTY, URL));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_create_webhook_with_minimal_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhookResponse("webhook-123", "Test Webhook", URL, false).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Test Webhook",
          CreateWebhookTool.URL_PROPERTY, URL));

      assertResultEquals(result, """
        {
          "key" : "webhook-123",
          "name" : "Test Webhook",
          "url" : "https://example.com/webhook",
          "hasSecret" : false
        }""");
      
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "name=Test+Webhook&url=" + urlEncode(URL)));
    }

    @SonarQubeMcpServerTest
    void it_should_create_webhook_with_all_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhookResponse("webhook-456", "My Project Webhook", URL, true).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "My Project Webhook",
          CreateWebhookTool.URL_PROPERTY, URL,
          CreateWebhookTool.PROJECT_PROPERTY, "my-project",
          CreateWebhookTool.SECRET_PROPERTY, "my-secret-key-123"));

      assertResultEquals(result, """
        {
          "key" : "webhook-456",
          "name" : "My Project Webhook",
          "url" : "https://example.com/webhook",
          "hasSecret" : true
        }""");
      
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "name=My+Project+Webhook&url=" + urlEncode(URL) + "&project=my-project&secret=my-secret-key-123"));
    }

    @SonarQubeMcpServerTest
    void it_should_create_webhook_with_project_only(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhookResponse("webhook-789", "Project Webhook", URL, false).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Project Webhook",
          CreateWebhookTool.URL_PROPERTY, URL,
          CreateWebhookTool.PROJECT_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "key" : "webhook-789",
          "name" : "Project Webhook",
          "url" : "https://example.com/webhook",
          "hasSecret" : false
        }""");
      
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "name=Project+Webhook&url=" + urlEncode(URL) + "&project=my-project"));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Test Webhook",
          CreateWebhookTool.URL_PROPERTY, URL));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_create_webhook_with_minimal_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhookResponse("webhook-123", "Test Webhook", URL, false).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Test Webhook",
          CreateWebhookTool.URL_PROPERTY, URL));

      assertResultEquals(result, """
        {
          "key" : "webhook-123",
          "name" : "Test Webhook",
          "url" : "https://example.com/webhook",
          "hasSecret" : false
        }""");
      
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "name=Test+Webhook&url=" + urlEncode(URL)));
    }

    @SonarQubeMcpServerTest
    void it_should_create_webhook_with_all_parameters(SonarQubeMcpServerTestHarness harness) {
      var url = "https://example.com/project-webhook";
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateWebhookResponse("webhook-456", "My Project Webhook", url, true).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "My Project Webhook",
          CreateWebhookTool.URL_PROPERTY, url,
          CreateWebhookTool.PROJECT_PROPERTY, "my-project",
          CreateWebhookTool.SECRET_PROPERTY, "my-secret-key-123"));

      assertResultEquals(result, """
        {
          "key" : "webhook-456",
          "name" : "My Project Webhook",
          "url" : "https://example.com/project-webhook",
          "hasSecret" : true
        }""");

      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", "name=My+Project+Webhook&url=" + urlEncode(url) + "&project=my-project&secret=my-secret-key-123"));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_server_error_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(post(WebhooksApi.CREATE_PATH).willReturn(aResponse().withStatus(500)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        CreateWebhookTool.TOOL_NAME,
        Map.of(
          CreateWebhookTool.NAME_PROPERTY, "Test Webhook",
          CreateWebhookTool.URL_PROPERTY, URL));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/webhooks/create", true));
    }
  }

  private static String generateWebhookResponse(String key, String name, String url, boolean hasSecret) {
    return """
      {
        "webhook": {
          "key": "%s",
          "name": "%s",
          "url": "%s",
          "hasSecret": %s
        }
      }
      """.formatted(key, name, url, hasSecret);
  }

}
