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
package org.sonarsource.sonarqube.mcp.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolExecutor;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureSetTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectureDeleteRecoveryTest {

  private static final String BASE_URL = "https://sonarcloud.io";
  private static final String BRANCH_URL = BASE_URL + "/api/project_branches/list?project=my_project";
  private static final String MODELS_URL = "https://api.sonarcloud.io/architecture/intended-architectures?projectId=main-branch";
  private static final String DELETE_URL = "https://api.sonarcloud.io/architecture/intended-architectures/11111111-1111-4111-8111-111111111111";
  private static final String SENTINEL = "private-deadline-recovery-pattern-sentinel";
  private static final String PREVIOUS = """
    {"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java",
     "groups":[{"label":"app","patterns":["private-deadline-recovery-pattern-sentinel"]}],"constraints":[]}
    """;

  @ParameterizedTest(name = "DELETE response cancelled={0}")
  @ValueSource(booleans = {false, true})
  void it_should_restore_after_a_committed_delete_loses_its_response(boolean cancelled) {
    var apacheClient = mock(CloseableHttpAsyncClient.class);
    var server = new CommittedDeleteServer(cancelled);
    when(apacheClient.execute(any(SimpleHttpRequest.class),
      org.mockito.ArgumentMatchers.<FutureCallback<SimpleHttpResponse>>any()))
      .thenAnswer(invocation -> server.respond(invocation.getArgument(0), invocation.getArgument(1)));
    var helper = new ServerApiHelper(new EndpointParams(BASE_URL, null, null, true), new HttpClientAdapter(apacheClient, "token", false));
    var api = new ServerApi(helper, true);
    var tool = new ArchitectureSetTool(() -> api, true, null);
    var executor = new ToolExecutor(mock(BackendService.class), null, null, null, null, ToolCategory::all);
    var logger = (Logger) LoggerFactory.getLogger(McpLogger.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    var originalErr = System.err;
    var stderr = new ByteArrayOutputStream();
    try (var capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
      System.setErr(capturedErr);
      var result = executor.execute(tool, McpSchema.CallToolRequest.builder(ArchitectureSetTool.TOOL_NAME)
        .arguments(Map.of("target", "intended", "projectKey", "my_project", "dryRun", false,
          "model", "[" + PREVIOUS.replace(SENTINEL, "proposed-pattern") + "]")).build());

      assertRecovered(result, server);
      assertThat(stderr.toString(StandardCharsets.UTF_8)).doesNotContain(SENTINEL, "Previous exported model");
      assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
        assertThat(event.getFormattedMessage()).doesNotContain(SENTINEL, "Previous exported model");
        assertThat(event.getThrowableProxy()).isNull();
      });
    } finally {
      System.setErr(originalErr);
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static void assertRecovered(McpSchema.CallToolResult result, CommittedDeleteServer server) {
    assertThat(result.isError()).isTrue();
    assertThat(result.content().toString()).contains("recreated and verified", "Previous exported model", SENTINEL);
    assertThat(server.storedModels.get()).isEqualTo("[" + PREVIOUS + "]");
    var expectedPost = JsonParser.parseString(PREVIOUS).getAsJsonObject();
    expectedPost.remove("id");
    assertThat(server.postedBodies).singleElement().satisfies(body -> assertThat(JsonParser.parseString(body)).isEqualTo(expectedPost));
    assertThat(server.requests).containsExactly("GET " + BRANCH_URL, "GET " + MODELS_URL, "GET " + MODELS_URL,
      "DELETE " + DELETE_URL, "GET " + MODELS_URL, "POST " + MODELS_URL, "GET " + MODELS_URL);
  }

  private static final class CommittedDeleteServer {
    private final boolean cancelled;
    private final AtomicReference<String> storedModels = new AtomicReference<>("[" + PREVIOUS + "]");
    private final List<String> requests = new ArrayList<>();
    private final List<String> postedBodies = new ArrayList<>();

    private CommittedDeleteServer(boolean cancelled) {
      this.cancelled = cancelled;
    }

    private CompletableFuture<SimpleHttpResponse> respond(SimpleHttpRequest request,
      FutureCallback<SimpleHttpResponse> callback) throws Exception {
      var url = request.getUri().toString();
      requests.add(request.getMethod() + " " + url);
      if ("DELETE".equals(request.getMethod())) {
        assertThat(url).isEqualTo(DELETE_URL);
        storedModels.set("[]");
        return failDeleteResponse(callback);
      }
      var response = response(request, url);
      callback.completed(response);
      return CompletableFuture.completedFuture(response);
    }

    private CompletableFuture<SimpleHttpResponse> failDeleteResponse(FutureCallback<SimpleHttpResponse> callback) {
      var future = new CompletableFuture<SimpleHttpResponse>();
      if (cancelled) {
        future.cancel(true);
        callback.cancelled();
      } else {
        var failure = new TimeoutException("API operation deadline elapsed");
        future.completeExceptionally(failure);
        callback.failed(failure);
      }
      return future;
    }

    private SimpleHttpResponse response(SimpleHttpRequest request, String url) {
      if (BRANCH_URL.equals(url)) {
        return jsonResponse(200, "{\"branches\":[{\"isMain\":true,\"branchId\":\"main-branch\"}]}");
      }
      assertThat(url).isEqualTo(MODELS_URL);
      if ("POST".equals(request.getMethod())) {
        assertThat(storedModels.get()).isEqualTo("[]");
        postedBodies.add(request.getBodyText());
        storedModels.set("[" + PREVIOUS + "]");
        return jsonResponse(201, PREVIOUS);
      }
      return jsonResponse(200, "{\"intendedArchitectures\":" + storedModels.get() + "}");
    }

    private static SimpleHttpResponse jsonResponse(int status, String body) {
      var response = SimpleHttpResponse.create(status);
      response.setBody(body, ContentType.APPLICATION_JSON);
      return response;
    }
  }

}
