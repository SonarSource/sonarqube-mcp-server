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

import com.google.gson.Gson;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.SMALL_JAVA_GRAPH;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.currentArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;

class ArchitectureHttpTest {

  @SonarQubeMcpServerTest
  void it_should_keep_http_recovery_exports_out_of_server_logs(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var sentinel = "private-http-recovery-pattern-sentinel";
    ArchitectureFixtures.stubMainBranch(harness);
    var path = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + ArchitectureFixtures.MAIN_BRANCH_ID;
    harness.getMockSonarQubeServer().stubFor(get(path)
      .willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL.replace("app:com.acme.domain.**", sentinel)))));
    harness.getMockSonarQubeServer().stubFor(patch(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "/11111111-1111-4111-8111-111111111111")
      .willReturn(aResponse().withStatus(500)));
    var port = port();
    var server = new SonarQubeMcpServer(environment(harness, storage, port));
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.sonarsource.sonarqube.mcp.log.McpLogger.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    var originalErr = System.err;
    var stderr = new java.io.ByteArrayOutputStream();
    try (var client = HttpClient.newHttpClient();
      var capturedErr = new java.io.PrintStream(stderr, true, java.nio.charset.StandardCharsets.UTF_8)) {
      System.setErr(capturedErr);
      server.start();
      var response = send(client, port, "a", false, "tools/call", Map.of("name", "architecture_set", "arguments",
        Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL.replace("domain", "infra"), "dryRun", false)));
      assertThat(response).contains("Previous exported model", sentinel, "isError\":true");
      assertThat(stderr.toString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(sentinel, "Previous exported model");
      assertThat(appender.list).allSatisfy(event -> {
        assertThat(event.getFormattedMessage()).doesNotContain(sentinel, "Previous exported model");
        assertThat(event.getThrowableProxy()).isNull();
      });
    } finally {
      System.setErr(originalErr);
      logger.detachAppender(appender);
      appender.stop();
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_isolate_architecture_organizations_and_apply_request_read_only_controls(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    for (var suffix : new String[] {"a", "b"}) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
        .withHeader("Authorization", equalTo("Bearer token-" + suffix))
        .willReturn(okJson("[{\"key\":\"org-" + suffix + "\",\"uuidV4\":\"uuid-" + suffix + "\"}]")));
      harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
        .withHeader("Authorization", equalTo("Bearer token-" + suffix))
        .willReturn(okJson("{\"branches\":[{\"isMain\":true,\"branchId\":\"branch-" + suffix + "\"}]}")));
      harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.CURRENT_ARCHITECTURES_PATH + "?organizationId=uuid-" + suffix + "&branchId=branch-" + suffix + "&language=java")
        .withHeader("Authorization", equalTo("Bearer token-" + suffix)).willReturn(okJson(currentArchitectures(SMALL_JAVA_GRAPH.replace("app", "app-" + suffix)))));
      harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=branch-" + suffix)
        .withHeader("Authorization", equalTo("Bearer token-" + suffix)).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    }
    var port = port();
    var server = new SonarQubeMcpServer(environment(harness, storage, port));
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      var tools = send(client, port, "a", false, "tools/list", Map.of());
      assertThat(tools).contains("architecture_get", "architecture_set").doesNotContain("architecture_get_current", "architecture_set_intended");
      for (var suffix : new String[] {"a", "b", "a"}) {
        var graph = send(client, port, suffix, false, "tools/call", Map.of("name", "architecture_get", "arguments",
          Map.of("target", "current", "projectKey", "my_project", "language", "java", "organization", "org-" + suffix)));
        assertThat(graph).contains("app-" + suffix).doesNotContain("isError\":true");
        var preview = send(client, port, suffix, false, "tools/call", Map.of("name", "architecture_set", "arguments",
          Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", true, "organization", "org-" + suffix)));
        assertThat(preview).contains("dry-run", "patternId").doesNotContain("isError\":true");
      }
      assertThat(send(client, port, "a", true, "tools/list", Map.of())).contains("architecture_get").doesNotContain("architecture_set");
      var rejected = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_set", "arguments",
        Map.of("target", "intended", "projectKey", "my_project", "model", INTENDED_JAVA_MODEL, "dryRun", false)));
      assertThat(rejected).doesNotContain("Nothing was changed");
      assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", "/architecture/")).isEmpty();
      assertThat(harness.getMockSonarQubeServer().getReceivedBodies("DELETE", "/architecture/")).isEmpty();
      assertThat(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", "/architecture/")).isEmpty();
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_read_preview_and_create_directives_over_http_with_request_read_only_controls(SonarQubeMcpServerTestHarness harness,
    @TempDir Path storage) throws Exception {
    ArchitectureFixtures.stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var path = ArchitectureApi.PROJECT_DIRECTIVES_PATH;
    var input = """
      {"type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph","fromNodeKey":"app:a","toNodeKey":"app:b"}
      """.trim();
    var id = "00000000-0000-4000-8000-000000000001";
    var saved = input.substring(0, input.length() - 1) + ",\"id\":\"" + id + "\",\"projectId\":\""
      + ArchitectureFixtures.MAIN_BRANCH_ID + "\",\"graphPerspective\":\"default_perspective\",\"origin\":\"sonarqube-mcp-server\"}";
    backend.stubFor(get(path + "?projectId=" + ArchitectureFixtures.MAIN_BRANCH_ID + "&pageIndex=1&pageSize=100")
      .withHeader("Authorization", equalTo("Bearer token-a"))
      .willReturn(okJson("{\"projectDirectives\":[],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}")));
    backend.stubFor(get(path + "/" + id).withHeader("Authorization", equalTo("Bearer token-a")).willReturn(okJson(saved)));
    backend.stubFor(post(path).withHeader("Authorization", equalTo("Bearer token-a"))
      .willReturn(aResponse().withStatus(201).withBody(saved)));
    var port = port();
    var server = new SonarQubeMcpServer(environment(harness, storage, port));
    var model = "{\"create\":[" + input + "]}";
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      var read = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_get", "arguments",
        Map.of("target", "directives", "projectKey", "my_project", "directiveId", id)));
      assertThat(read).contains(id).doesNotContain("isError\":true");
      var preview = send(client, port, "a", false, "tools/call", Map.of("name", "architecture_set", "arguments",
        Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", true)));
      assertThat(preview).contains("dry-run", "toCreate").doesNotContain("isError\":true");
      assertThat(backend.getReceivedBodies("POST", path)).isEmpty();
      var before = backend.getReceivedRequests().size();
      var rejected = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_set", "arguments",
        Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", false)));
      assertThat(rejected).contains("Tool not found: architecture_set");
      assertThat(backend.getReceivedRequests()).hasSize(before);
      var applied = send(client, port, "a", false, "tools/call", Map.of("name", "architecture_set", "arguments",
        Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", false)));
      assertThat(applied).contains("applied", id).doesNotContain("isError\":true");
      assertThat(backend.getReceivedBodies("POST", path)).singleElement()
        .satisfies(body -> assertThat(body).contains("sonarqube-mcp-server", "default_perspective"));
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_read_findings_over_http_with_request_credentials(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    ArchitectureFixtures.stubMainBranch(harness);
    var finding = "{\"id\":\"finding-1\",\"projectId\":\"" + ArchitectureFixtures.MAIN_BRANCH_ID + "\",\"category\":\"FLAW\"}";
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.PROJECT_FINDINGS_PATH + "?projectId=" + ArchitectureFixtures.MAIN_BRANCH_ID
      + "&category=FLAW&pageIndex=1&pageSize=50").withHeader("Authorization", equalTo("Bearer token-a"))
      .willReturn(okJson("{\"projectFindings\":[" + finding + "],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}")));
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.PROJECT_FINDINGS_PATH + "/finding-1")
      .withHeader("Authorization", equalTo("Bearer token-a")).willReturn(okJson(finding)));
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.PROJECT_FINDINGS_PATH + "/forbidden")
      .withHeader("Authorization", equalTo("Bearer token-a")).willReturn(aResponse().withStatus(403)));
    var port = port();
    var server = new SonarQubeMcpServer(environment(harness, storage, port));
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      var read = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_get", "arguments",
        Map.of("target", "findings", "projectKey", "my_project", "category", "FLAW")));
      assertThat(read).contains("finding-1").doesNotContain("isError\":true");
      var detail = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_get", "arguments",
        Map.of("target", "findings", "projectKey", "my_project", "findingId", "finding-1")));
      assertThat(detail).contains("FLAW").doesNotContain("isError\":true");
      var denied = send(client, port, "a", true, "tools/call", Map.of("name", "architecture_get", "arguments",
        Map.of("target", "findings", "projectKey", "my_project", "findingId", "forbidden")));
      assertThat(denied).contains("isError\":true", "refusal to read the architecture");
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_hide_architecture_set_in_server_read_only_mode(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = port();
    var env = environment(harness, storage, port);
    env.put("SONARQUBE_READ_ONLY", "true");
    var server = new SonarQubeMcpServer(env);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      assertThat(send(client, port, "a", false, "tools/list", Map.of())).contains("architecture_get").doesNotContain("architecture_set");
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_advertise_architecture_operations_over_server_http(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = port();
    var env = environment(harness, storage, port);
    env.put("SONARQUBE_IS_CLOUD", "false");
    harness.prepareMockWebServer(env);
    var server = new SonarQubeMcpServer(env);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      var response = send(client, port, "a", false, "tools/list", Map.of());
      for (var suffix : new String[] {"a", "b", "a"}) {
        harness.getMockSonarQubeServer().stubFor(get("/api/v2/components/projects?keys=my_project&pageSize=1")
          .withHeader("Authorization", equalTo("Bearer token-" + suffix))
          .willReturn(okJson("{\"projects\":[{\"id\":\"project-" + suffix + "\",\"key\":\"my_project\"}],\"page\":{\"total\":1}}")));
        harness.getMockSonarQubeServer().stubFor(get("/api/v2/architecture/intended-architectures?projectId=project-" + suffix)
          .withHeader("Authorization", equalTo("Bearer token-" + suffix))
          .willReturn(okJson("{\"id\":null,\"intendedArchitectures\":[]}")));
        assertThat(send(client, port, suffix, false, "tools/call", Map.of("name", "architecture_get", "arguments",
          Map.of("target", "intended", "projectKey", "my_project")))).doesNotContain("isError\":true");
        for (var target : new String[] {"directives", "findings"}) {
          var resource = "project-" + target;
          var envelope = "directives".equals(target) ? "projectDirectives" : "projectFindings";
          harness.getMockSonarQubeServer().stubFor(get("/api/v2/architecture/" + resource + "?projectId=project-" + suffix + "&pageIndex=1&pageSize=50")
            .withHeader("Authorization", equalTo("Bearer token-" + suffix))
            .willReturn(okJson("{\"" + envelope + "\":[],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":0}}")));
          assertThat(send(client, port, suffix, true, "tools/call", Map.of("name", "architecture_get", "arguments",
            Map.of("target", target, "projectKey", "my_project")))).contains(target).doesNotContain("isError\":true");
        }
      }
      assertThat(response).contains("architecture_get", "architecture_set");
      var tools = com.google.gson.JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools");
      var read = tools.asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
        .filter(tool -> tool.get("name").getAsString().equals("architecture_get")).findFirst().orElseThrow();
      assertThat(read.getAsJsonObject("inputSchema").getAsJsonObject("properties").getAsJsonObject("target").getAsJsonArray("enum"))
        .hasToString("[\"current\",\"intended\",\"directives\",\"findings\"]");
    } finally {
      server.shutdown();
    }
  }

  private static Map<String, String> environment(SonarQubeMcpServerTestHarness harness, Path storage, int port) {
    return new HashMap<>(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TRANSPORT", "http", "SONARQUBE_HTTP_PORT", Integer.toString(port),
      "SONARQUBE_IS_CLOUD", "true", "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "SONARQUBE_TOOLSETS", "architecture", "TELEMETRY_DISABLED", "true"));
  }

  private static String send(HttpClient client, int port, String suffix, boolean readOnly, String method, Map<String, Object> params) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
      .header("Authorization", "Bearer token-" + suffix).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .header("SONARQUBE_READ_ONLY", Boolean.toString(readOnly))
      .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)))).build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private static int port() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
