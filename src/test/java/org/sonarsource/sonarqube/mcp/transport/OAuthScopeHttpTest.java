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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.OAuthProtectedResourceMetadata;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthScopeHttpTest {
  private final AtomicInteger executions = new AtomicInteger();
  private final AtomicInteger entitlementChecks = new AtomicInteger();
  private final OAuthProtectedResourceMetadata metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://tenant.auth0.com/");
  private final HttpClient client = HttpClient.newHttpClient();
  private Server server;
  private McpStatelessSyncServer mcp;
  private URI endpoint;

  @BeforeEach
  void start() throws Exception {
    var tools = List.of(tool("read_tool", true, false), tool("write_tool", false, false), tool("both_tool", false, true));
    var transport = HttpServletStatelessServerTransport.builder().messageEndpoint("/mcp").jsonMapper(McpJsonMappers.DEFAULT)
      .contextExtractor(request -> (McpTransportContext) request.getAttribute("test-context")).build();
    var filtering = new McpStatelessServerTransport() {
      @Override
      public void setMcpHandler(McpStatelessServerHandler handler) {
        transport.setMcpHandler(new PerRequestToolFilteringHandler(handler, tools));
      }

      @Override
      public Mono<Void> closeGracefully() {
        return transport.closeGracefully();
      }

      @Override
      public List<String> protocolVersions() {
        return transport.protocolVersions();
      }
    };
    mcp = McpServer.sync(filtering).serverInfo("scope-tests", "1")
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
      .tools(tools.stream().map(tool -> new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(tool.definition())
        .callHandler((context, request) -> tool.execute(new Tool.Arguments(request.arguments(), null)).toCallToolResult()).build())
        .toArray(McpStatelessServerFeatures.SyncToolSpecification[]::new)).build();
    var servletContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    servletContext.setContextPath("/");
    servletContext.addFilter(new FilterHolder((Filter) (request, response, chain) -> {
      var http = (HttpServletRequest) request;
      var scopes = grants(http.getHeader("Authorization").substring("Bearer ".length()));
      var authorization = new OAuthScopeContext(scopes);
      var values = new HashMap<String, Object>();
      values.put(OAuthScopeContext.CONTEXT_KEY, authorization);
      values.put(HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, Boolean.parseBoolean(http.getHeader("SONARQUBE_READ_ONLY")));
      if (http.getHeader("SONARQUBE_TOOLSETS") != null) {
        values.put(HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, ToolCategory.parseCategories(http.getHeader("SONARQUBE_TOOLSETS")));
      }
      request.setAttribute("test-context", McpTransportContext.create(values));
      chain.doFilter(request, new OAuthScopeResponseWrapper((HttpServletResponse) response, authorization, metadata));
    }), "/*", EnumSet.of(DispatcherType.REQUEST));
    servletContext.addServlet(new ServletHolder(transport), "/*");
    server = new Server(0);
    server.setHandler(servletContext);
    server.start();
    endpoint = URI.create("http://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort() + "/mcp");
  }

  @AfterEach
  void stop() throws Exception {
    client.close();
    mcp.closeGracefully();
    server.stop();
  }

  @ParameterizedTest
  @MethodSource("scopeCases")
  void it_should_list_and_call_only_tools_whose_scopes_are_granted(String grant, Set<String> visible) throws Exception {
    var listed = send(grant, "tools/list", Map.of(), Map.of());
    assertThat(listed.statusCode()).isEqualTo(200);
    var document = JsonParser.parseString(listed.body()).getAsJsonObject();
    var names = document.getAsJsonObject("result").getAsJsonArray("tools").asList().stream()
      .map(JsonElement::getAsJsonObject).map(tool -> tool.get("name").getAsString()).toList();
    assertThat(names).containsExactlyInAnyOrderElementsOf(visible);
    assertThat(listed.body()).doesNotContain("requiredOAuthScopes", "oauth-scopes");
    assertThat(entitlementChecks.get()).isEqualTo(visible.size());

    for (var name : List.of("read_tool", "write_tool", "both_tool")) {
      entitlementChecks.set(0);
      var response = send(grant, "tools/call", Map.of("name", name, "arguments", Map.of()), Map.of());
      if (visible.contains(name)) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(entitlementChecks.get()).isEqualTo(1);
      } else {
        var required = switch (name) {
          case "read_tool" -> Set.of("read:all");
          case "write_tool" -> Set.of("write:all");
          default -> Set.of("read:all", "write:all");
        };
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains(metadata.challenge(required) + ", error=\"insufficient_scope\"");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).contains("Insufficient OAuth scope");
        assertThat(entitlementChecks.get()).isZero();
      }
    }
    assertThat(executions.get()).isEqualTo(visible.size());
  }

  static Stream<Arguments> scopeCases() {
    return Stream.of(Arguments.of("read", Set.of("read_tool")), Arguments.of("write", Set.of("write_tool")),
      Arguments.of("both", Set.of("read_tool", "write_tool", "both_tool")), Arguments.of("none", Set.of()));
  }

  @Test
  void it_should_preserve_unknown_tool_and_ordinary_filter_errors_without_scope_challenges() throws Exception {
    var unknown = send("none", "tools/call", Map.of("name", "unknown", "arguments", Map.of()), Map.of());
    var readOnly = send("none", "tools/call", Map.of("name", "write_tool", "arguments", Map.of()), Map.of("SONARQUBE_READ_ONLY", "true"));
    var toolsets = send("none", "tools/call", Map.of("name", "read_tool", "arguments", Map.of()), Map.of("SONARQUBE_TOOLSETS", "measures"));

    for (var response : List.of(unknown, readOnly, toolsets)) {
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("-32601", "Tool not found");
      assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }
    assertThat(executions.get()).isZero();
    assertThat(entitlementChecks.get()).isZero();
  }

  @Test
  void it_should_apply_read_only_preference_independently_of_oauth_grants() throws Exception {
    var readOnly = send("both", "tools/list", Map.of(), Map.of("SONARQUBE_READ_ONLY", "true"));
    assertThat(readOnly.body()).contains("read_tool").doesNotContain("write_tool", "both_tool");

    var denial = send("read", "tools/call", Map.of("name", "write_tool", "arguments", Map.of()), Map.of("SONARQUBE_READ_ONLY", "false"));
    assertThat(denial.statusCode()).isEqualTo(403);
    assertThat(executions.get()).isZero();
  }

  @Test
  void it_should_isolate_scope_denials_between_concurrent_requests() {
    var responses = new ArrayList<CompletableFuture<Void>>();
    for (var i = 0; i < 20; i++) {
      var allowed = i % 2 == 0;
      responses.add(client.sendAsync(request(allowed ? "read" : "write", "tools/call",
        Map.of("name", "read_tool", "arguments", Map.of()), Map.of()), HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
          assertThat(response.statusCode()).isEqualTo(allowed ? 200 : 403);
          assertThat(response.headers().firstValue("WWW-Authenticate").isPresent()).isEqualTo(!allowed);
        }));
    }
    CompletableFuture.allOf(responses.toArray(CompletableFuture[]::new)).join();
    assertThat(executions.get()).isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"application/json", "text/event-stream"})
  void it_should_preserve_sdk_accept_rejections_without_a_scope_challenge(String accept) throws Exception {
    var response = send("none", "tools/call", Map.of("name", "read_tool", "arguments", Map.of()), Map.of("Accept", accept));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    assertThat(executions.get()).isZero();
  }

  @Test
  void it_should_challenge_with_either_order_of_the_supported_accept_types() throws Exception {
    var response = send("none", "tools/call", Map.of("name", "read_tool", "arguments", Map.of()),
      Map.of("Accept", "text/event-stream, application/json"));

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("WWW-Authenticate")).contains(metadata.challenge(Set.of("read:all")) + ", error=\"insufficient_scope\"");
  }

  @Test
  void it_should_preserve_notification_status_and_not_leak_a_prior_denial() throws Exception {
    assertThat(send("none", "tools/call", Map.of("name", "read_tool", "arguments", Map.of()), Map.of()).statusCode()).isEqualTo(403);
    var notification = HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer read")
      .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")).build();
    var notified = client.send(notification, HttpResponse.BodyHandlers.ofString());
    assertThat(notified.statusCode()).isEqualTo(202);
    assertThat(notified.headers().firstValue("WWW-Authenticate")).isEmpty();

    var allowed = send("read", "tools/call", Map.of("name", "read_tool", "arguments", Map.of()), Map.of());
    assertThat(allowed.statusCode()).isEqualTo(200);
    assertThat(allowed.headers().firstValue("WWW-Authenticate")).isEmpty();
  }

  private Tool tool(String name, boolean readOnly, boolean requiresBothScopes) {
    var definition = ToolDefinitionBuilder.builder().setName(name).setDescription(name);
    if (readOnly) {
      definition.setReadOnlyHint();
    }
    return new Tool(definition.build(), ToolCategory.ISSUES) {
      @Override
      public Set<String> requiredOAuthScopes() {
        return requiresBothScopes ? Set.of("read:all", "write:all") : super.requiredOAuthScopes();
      }

      @Override
      public boolean isEnabledFor(McpTransportContext context) {
        entitlementChecks.incrementAndGet();
        return true;
      }

      @Override
      public Result execute(Arguments arguments) {
        executions.incrementAndGet();
        return new Result(McpSchema.CallToolResult.builder().addTextContent("executed").isError(false).build());
      }
    };
  }

  private HttpResponse<String> send(String grant, String method, Map<String, Object> params, Map<String, String> headers) throws Exception {
    return client.send(request(grant, method, params, headers), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest request(String grant, String method, Map<String, Object> params, Map<String, String> headers) {
    var builder = HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + grant)
      .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))));
    headers.forEach(builder::setHeader);
    return builder.build();
  }

  private static Set<String> grants(String token) {
    return switch (token) {
      case "read" -> Set.of("read:all");
      case "write" -> Set.of("write:all");
      case "both" -> Set.of("read:all", "write:all");
      default -> Set.of();
    };
  }
}
