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
package org.sonarsource.sonarqube.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;

class SonarQubeMcpServerStartupFailureTest {

  private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n";

  @SonarQubeMcpServerTest
  void it_should_not_initialize_backend_when_sca_startup_probe_fails(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(FeaturesApi.FEATURES_LIST_PATH).willReturn(aResponse().withStatus(500)));
    var environment = Map.of("SONARQUBE_TOOLSETS", "dependency-risks");
    try (var backends = mockConstruction(BackendService.class)) {
      assertThatThrownBy(() -> harness.newClient(environment))
        .isInstanceOf(ServerInternalErrorException.class);
      assertThat(backends.constructed()).hasSize(1);
      verifyNoInteractions(backends.constructed().getFirst());
    }
  }

  @Test
  void should_not_write_to_stdout_when_http_transport_is_enabled() {
    var out = new ByteArrayOutputStream();

    SonarQubeMcpServer.handleStartupFailure(
      new IllegalArgumentException("invalid keystore"),
      Map.of("SONARQUBE_TRANSPORT", "http"),
      new ByteArrayInputStream(INITIALIZE.getBytes(StandardCharsets.UTF_8)),
      out);

    assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void should_not_write_to_stdout_when_https_transport_is_enabled() {
    var out = new ByteArrayOutputStream();

    SonarQubeMcpServer.handleStartupFailure(
      new IllegalArgumentException("invalid keystore"),
      Map.of("SONARQUBE_TRANSPORT", "https"),
      new ByteArrayInputStream(INITIALIZE.getBytes(StandardCharsets.UTF_8)),
      out);

    assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void should_reply_to_initialize_when_stdio_startup_fails() throws IOException {
    var out = new ByteArrayOutputStream();

    SonarQubeMcpServer.handleStartupFailure(
      new IllegalArgumentException("SONARQUBE_TOKEN environment variable or property must be set"),
      Map.of(),
      new ByteArrayInputStream(INITIALIZE.getBytes(StandardCharsets.UTF_8)),
      out);

    var parsed = McpSchema.deserializeJsonRpcMessage(McpJsonMappers.DEFAULT, out.toString(StandardCharsets.UTF_8).trim());
    assertThat(parsed).isInstanceOf(McpSchema.JSONRPCResponse.class);
    var response = (McpSchema.JSONRPCResponse) parsed;
    assertThat(response.id()).isEqualTo(1);
    assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.INTERNAL_ERROR);
    assertThat(response.error().message()).contains("SONARQUBE_TOKEN");
  }
}
