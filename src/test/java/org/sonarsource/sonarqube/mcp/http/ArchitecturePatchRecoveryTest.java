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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitecturePatchRecoveryTest {

  private static final String BASE_URL = "https://sonarcloud.io";
  private static final String BRANCH_URL = BASE_URL + "/api/project_branches/list?project=my_project";
  private static final String MODELS_URL = "https://api.sonarcloud.io/architecture/intended-architectures?projectId=main-branch";
  private static final String PATCH_URL = "https://api.sonarcloud.io/architecture/intended-architectures/11111111-1111-4111-8111-111111111111";
  private static final String SENTINEL = "private-patch-recovery-pattern-sentinel";
  private static final String PREVIOUS = """
    {"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java",
     "groups":[{"label":"app","patterns":["private-patch-recovery-pattern-sentinel"]}],"constraints":[]}
    """;
  private static final String NEXT = PREVIOUS.replace(SENTINEL, "proposed-pattern");

  @ParameterizedTest(name = "PATCH cancelled={0}, committed={1}")
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void it_should_reconcile_patch_failures_without_rollback_or_logging_exports(boolean cancelled, boolean committed) {
    var client = mock(HttpClient.class);
    var stored = new AtomicReference<>(PREVIOUS);
    var requestBody = new AtomicReference<String>();
    when(client.getAsync(BRANCH_URL)).thenAnswer(invocation -> response("{\"branches\":[{\"isMain\":true,\"branchId\":\"main-branch\"}]}"));
    when(client.getAsync(MODELS_URL)).thenAnswer(invocation -> response("{\"intendedArchitectures\":[" + stored.get() + "]}"));
    when(client.patchAsync(eq(PATCH_URL), eq(HttpClient.MERGE_PATCH_CONTENT_TYPE), anyString())).thenAnswer(invocation -> {
      requestBody.set(invocation.getArgument(2));
      if (committed) {
        stored.set(NEXT);
      }
      return failedWrite(cancelled);
    });
    var helper = new ServerApiHelper(new EndpointParams(BASE_URL, null, null, true), client);
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
        .arguments(Map.of("target", "intended", "projectKey", "my_project", "dryRun", false, "model", "[" + NEXT + "]")).build());

      assertThat(result.isError()).isEqualTo(!committed);
      if (committed) {
        assertThat(result.content().toString()).contains("replaced").doesNotContain("Previous exported model");
      } else {
        assertThat(result.content().toString()).contains("No automatic rollback", "Previous exported model", SENTINEL);
      }
      assertThat(stored.get()).isEqualTo(committed ? NEXT : PREVIOUS);
      assertPatchRequest(requestBody.get());
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
    verify(client, never()).postAsync(anyString(), anyString(), anyString());
    var order = inOrder(client);
    order.verify(client).getAsync(BRANCH_URL);
    order.verify(client, org.mockito.Mockito.times(2)).getAsync(MODELS_URL);
    order.verify(client).patchAsync(eq(PATCH_URL), eq(HttpClient.MERGE_PATCH_CONTENT_TYPE), anyString());
    order.verify(client).getAsync(MODELS_URL);
    order.verifyNoMoreInteractions();
  }

  private static void assertPatchRequest(String body) {
    var expected = JsonParser.parseString(NEXT).getAsJsonObject();
    expected.remove("id");
    var request = JsonParser.parseString(body).getAsJsonObject();
    assertThat(request.keySet()).containsExactly("intendedArchitectures");
    assertThat(request.getAsJsonArray("intendedArchitectures")).hasSize(1);
    assertThat(request.getAsJsonArray("intendedArchitectures").get(0)).isEqualTo(expected);
  }

  private static CompletableFuture<HttpClient.Response> failedWrite(boolean cancelled) {
    if (!cancelled) {
      return CompletableFuture.failedFuture(new TimeoutException("API operation deadline elapsed"));
    }
    var future = new CompletableFuture<HttpClient.Response>();
    future.cancel(true);
    return future;
  }

  private static CompletableFuture<HttpClient.Response> response(String body) {
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsString()).thenReturn(body);
    return CompletableFuture.completedFuture(response);
  }
}
