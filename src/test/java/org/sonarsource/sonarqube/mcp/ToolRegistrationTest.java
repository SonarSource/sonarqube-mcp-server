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

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistrationTest {

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @CsvSource({"projects,false", "projects,true", "architecture,false", "architecture,true", "agentic-readiness,false", "agentic-readiness,true"})
  void it_should_advertise_the_accepted_http_inventory_and_group_instructions(String toolset, boolean readOnly, @TempDir Path storage) throws Exception {
    var environment = environment(toolset, storage);
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "0");
    environment.put("SONARQUBE_READ_ONLY", Boolean.toString(readOnly));
    harness.prepareMockWebServer(environment);
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      server.waitForInitialization();
      var request = HttpRequest.newBuilder(URI.create(server.getHttpServerUrl()))
        .header("Authorization", "Bearer caller-token")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
        .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      var advertised = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools");
      assertThat(advertised.asList()).extracting(tool -> tool.getAsJsonObject().get("name").getAsString())
        .containsExactlyElementsOf(server.getRegisteredTools().stream().map(tool -> tool.definition().name()).toList());
      if (readOnly) {
        assertThat(server.getRegisteredTools()).allMatch(tool -> Boolean.TRUE.equals(tool.definition().annotations().readOnlyHint()));
      }
      assertGroupInstructions(server, toolset);
      assertThat(server.getComposedInstructions()).doesNotContain("Analyzers are being downloaded");
      assertThat(harness.getMockSonarQubeServer().hasReceivedInstalledPluginsRequest()).isFalse();
    } finally {
      server.shutdown();
    }
  }

  @Test
  void it_should_not_describe_or_download_analyzers_without_a_startup_token(@TempDir Path storage) throws Exception {
    var environment = environment("analysis", storage);
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "0");
    environment.remove("SONARQUBE_TOKEN");
    var server = new SonarQubeMcpServer(environment);
    try {
      server.start();
      server.waitForInitialization();
      assertThat(server.getRegisteredTools()).extracting(tool -> tool.definition().name()).doesNotContain("analyze_code_snippet");
      assertThat(server.getComposedInstructions()).doesNotContain("Analyzers are being downloaded");
      assertThat(harness.getMockSonarQubeServer().hasReceivedInstalledPluginsRequest()).isFalse();
    } finally {
      server.shutdown();
    }
  }

  @Test
  void it_should_preserve_proxy_instructions_when_all_proxy_tools_are_filtered(@TempDir Path storage) throws Exception {
    var script = Path.of(getClass().getResource("/test-mcp-server.py").toURI());
    var config = storage.resolve("proxies.json");
    Files.writeString(config, new Gson().toJson(List.of(Map.of("name", "test", "command", "python3",
      "args", List.of(script.toString(), "--instructions", "Runtime proxy guidance."), "supportedTransports", List.of("stdio")))));
    var property = "proxied.mcp.servers.config.path";
    var previousConfig = System.getProperty(property);
    var environment = environment("vortex", storage);
    environment.put("SONARQUBE_READ_ONLY", "true");
    harness.prepareMockWebServer(environment);
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(true);
    System.setProperty(property, config.toString());
    SonarQubeMcpServer server = null;
    try {
      server = new SonarQubeMcpServer(new StdioServerTransportProvider(null), null, environment);
      assertThat(server.getRegisteredTools()).noneMatch(ProxiedMcpTool.class::isInstance);
      assertThat(server.getComposedInstructions()).startsWith("Transform your code quality workflow").contains("Runtime proxy guidance.");
    } finally {
      if (server != null) {
        server.shutdown();
      }
      if (previousConfig == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previousConfig);
      }
    }
  }

  private Map<String, String> environment(String toolset, Path storage) {
    return new HashMap<>(Map.of("SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "SONARQUBE_TOKEN", "test-token",
      "SONARQUBE_ORG", "org", "SONARQUBE_TOOLSETS", toolset, "STORAGE_PATH", storage.toString(), "TELEMETRY_DISABLED", "true"));
  }

  private static void assertGroupInstructions(SonarQubeMcpServer server, String toolset) {
    var names = server.getRegisteredTools().stream().map(tool -> tool.definition().name()).toList();
    assertThat(server.getComposedInstructions().contains("## Architecture")).isEqualTo(toolset.equals("architecture"));
    assertThat(server.getComposedInstructions().contains("## Agentic Readiness Assessment")).isEqualTo(toolset.equals("agentic-readiness"));
    if (toolset.equals("architecture")) {
      assertThat(names).contains("architecture_get");
    } else if (toolset.equals("agentic-readiness")) {
      assertThat(names).contains("get_agentic_readiness_assessment", "list_agentic_readiness_assessments");
    }
  }
}
