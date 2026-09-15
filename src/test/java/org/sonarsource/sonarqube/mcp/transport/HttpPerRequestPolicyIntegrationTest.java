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
package org.sonarsource.sonarqube.mcp.transport;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HttpPerRequestPolicyIntegrationTest {

  private static final String TOKEN = "test-token";
  private static final String PROTOCOL_VERSION = "2025-03-26";
  private static final Map<String, Object> EMPTY_INPUT_SCHEMA = Map.of(
    "type", "object",
    "properties", Map.of(),
    "required", List.of(),
    "additionalProperties", false);

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private HttpServerTransportProvider httpServer;
  private McpStatelessSyncServer mcpServer;
  private int testPort;

  @BeforeEach
  void setUp() {
    sonarqubeMock.stubFor(post(urlEqualTo(IssuesApi.DO_TRANSITION_PATH)).willReturn(okJson("{}")));

    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", false);
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    var httpClient = new HttpClientProvider("HttpPerRequestPolicyIntegrationTest").getHttpClient(TOKEN);
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false), httpClient);
    var writeTool = new ChangeIssueStatusTool(() -> new ServerApi(helper, false));
    var projectsTool = readOnlyProjectsTool();
    var tools = List.of(writeTool, projectsTool);

    mcpServer = McpServer.sync(httpServer.getFilteringTransport(tools))
      .serverInfo(McpSchema.Implementation.builder("sonarqube-mcp-server", "1.0.0").build())
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
      .tools(toSpec(writeTool), toSpec(projectsTool))
      .build();
  }

  @AfterEach
  void tearDown() {
    if (mcpServer != null) {
      mcpServer.closeGracefully();
    }
    if (httpServer != null) {
      httpServer.stopServer().join();
    }
  }

  @Test
  void should_omit_write_tool_and_reject_call_when_read_only_header_is_true() throws Exception {
    assertWriteToolBlocked("SONARQUBE_READ_ONLY", "true", null, null);
  }

  @Test
  void should_omit_issues_write_tool_and_reject_call_when_toolsets_header_excludes_issues() throws Exception {
    assertWriteToolBlocked(null, null, "SONARQUBE_TOOLSETS", "measures");
  }

  @Test
  void should_list_write_tool_when_no_per_request_policy_header() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      initialize(client);
      var listed = toolNames(postMcp(client, toolsListBody(), null, null, null, null));
      assertThat(listed).contains(ChangeIssueStatusTool.TOOL_NAME);
    }
  }

  private void assertWriteToolBlocked(String readOnlyHeaderName, String readOnlyValue,
    String toolsetsHeaderName, String toolsetsValue) throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      initialize(client);

      var listResponse = postMcp(client, toolsListBody(), readOnlyHeaderName, readOnlyValue, toolsetsHeaderName, toolsetsValue);
      assertThat(listResponse.statusCode()).isEqualTo(200);
      var listed = toolNames(listResponse);
      assertThat(listed)
        .doesNotContain(ChangeIssueStatusTool.TOOL_NAME)
        .contains(SearchMyProjectsTool.TOOL_NAME);

      var callResponse = postMcp(client, toolsCallChangeIssueStatusBody(), readOnlyHeaderName, readOnlyValue,
        toolsetsHeaderName, toolsetsValue);
      assertThat(callResponse.statusCode()).isEqualTo(200);
      var payload = jsonRpcPayload(callResponse);
      assertThat(payload.has("error")).isTrue();
      assertThat(payload.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);

      sonarqubeMock.verify(0, postRequestedFor(urlEqualTo(IssuesApi.DO_TRANSITION_PATH)));
    }
  }

  private void initialize(HttpClient client) throws Exception {
    var response = postMcp(client, """
      {"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"%s","capabilities":{},"clientInfo":{"name":"policy-test","version":"1.0.0"}}}
      """.formatted(PROTOCOL_VERSION), null, null, null, null);
    assertThat(response.statusCode()).isLessThan(500);
  }

  private HttpResponse<String> postMcp(HttpClient client, String body,
    String readOnlyHeaderName, String readOnlyValue,
    String toolsetsHeaderName, String toolsetsValue) throws Exception {
    var requestBuilder = HttpRequest.newBuilder()
      .uri(URI.create(httpServer.getServerUrl()))
      .timeout(Duration.ofSeconds(5))
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .header("MCP-Protocol-Version", PROTOCOL_VERSION)
      .header("Authorization", "Bearer " + TOKEN)
      .POST(HttpRequest.BodyPublishers.ofString(body));
    if (readOnlyHeaderName != null) {
      requestBuilder.header(readOnlyHeaderName, readOnlyValue);
    }
    if (toolsetsHeaderName != null) {
      requestBuilder.header(toolsetsHeaderName, toolsetsValue);
    }
    return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String toolsListBody() {
    return """
      {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
      """;
  }

  private static String toolsCallChangeIssueStatusBody() {
    return """
      {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"%s","arguments":{"key":"ISSUE-1","status":"accept"}}}
      """.formatted(ChangeIssueStatusTool.TOOL_NAME);
  }

  private static List<String> toolNames(HttpResponse<String> response) {
    var payload = jsonRpcPayload(response);
    var tools = payload.getAsJsonObject("result").getAsJsonArray("tools");
    return StreamSupport.stream(tools.spliterator(), false)
      .map(element -> element.getAsJsonObject().get("name").getAsString())
      .toList();
  }

  private static JsonObject jsonRpcPayload(HttpResponse<String> response) {
    var body = response.body();
    var contentType = response.headers().firstValue("Content-Type").orElse("");
    var json = body;
    if (contentType.contains("text/event-stream") || body.startsWith("event:") || body.contains("\ndata:")) {
      json = Arrays.stream(body.split("\\R"))
        .filter(line -> line.startsWith("data:"))
        .map(line -> line.substring("data:".length()).trim())
        .filter(line -> !line.isEmpty() && !"[DONE]".equals(line))
        .collect(Collectors.joining());
    }
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private static McpStatelessServerFeatures.SyncToolSpecification toSpec(Tool tool) {
    return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
      .tool(tool.definition())
      .callHandler((transportContext, toolRequest) -> {
        var arguments = toolRequest.arguments() != null ? toolRequest.arguments() : Map.<String, Object>of();
        return tool.execute(new Tool.Arguments(arguments, toolRequest.meta())).toCallToolResult();
      })
      .build();
  }

  private static Tool readOnlyProjectsTool() {
    var annotations = new McpSchema.ToolAnnotations(null, true, false, false, false, null);
    var definition = McpSchema.Tool.builder(SearchMyProjectsTool.TOOL_NAME, EMPTY_INPUT_SCHEMA)
      .title("Search My SonarQube Projects")
      .description("Find SonarQube projects")
      .annotations(annotations)
      .build();
    return new Tool(definition, ToolCategory.PROJECTS) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("not invoked");
      }
    };
  }

  private boolean isServerRunning(String serverUrl) {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(1))
        .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 600;
    } catch (Exception e) {
      return false;
    }
  }

  private int findAvailablePort() {
    try (var serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to find available port", e);
    }
  }

}
