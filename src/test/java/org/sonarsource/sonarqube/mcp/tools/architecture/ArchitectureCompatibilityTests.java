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

import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureCompatibilityTests {
  private static final String COMPATIBILITY = "supported public Architecture API contract";

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  private void project() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get("/api/v2/components/projects?keys=my_project&pageSize=1")
      .willReturn(okJson("{\"projects\":[{\"key\":\"my_project\",\"id\":\"opaque-project-id\"}],\"page\":{\"total\":1}}")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_explain_incompatible_response_envelopes_without_writing(boolean cloud) {
    project();
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var paths = Map.of("current", "current-architectures", "intended", "project-intended-architectures", "directives", "project-directives", "findings", "project-findings");
    for (var target : paths.keySet()) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo((cloud ? "" : "/api/v2") + "/architecture/" + paths.get(target)))
        .willReturn(okJson("{}")));
      var arguments = new java.util.HashMap<String, Object>(Map.of("target", target, "projectKey", "my_project"));
      if (!target.equals("directives")) {
        arguments.put("language", "java");
      }
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains(COMPATIBILITY);
    }
    for (var method : new String[] {"POST", "PATCH", "DELETE"}) {
      assertThat(harness.getMockSonarQubeServer().getReceivedBodies(method, "/architecture/")).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_not_relabel_http_errors_or_empty_results_as_version_incompatibility(boolean cloud) {
    project();
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var path = (cloud ? "" : "/api/v2") + "/architecture/current-architectures";
    var arguments = Map.<String, Object>of("target", "current", "projectKey", "my_project", "language", "java");
    for (var status : new int[] {401, 403, 404, 500}) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withBody("{\"message\":\"Request refused\"}")));
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).doesNotContain(COMPATIBILITY);
    }
    var before = harness.getMockSonarQubeServer().countRequestsContaining("/architecture/");
    var invalid = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "current", "projectKey", "my_project", "depth", 0));
    assertThat(invalid.isError()).isTrue();
    assertThat(textOf(invalid)).doesNotContain(COMPATIBILITY);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/")).isEqualTo(before);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).willReturn(okJson("{\"currentArchitectures\":[]}")));
    var empty = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(empty.isError()).isFalse();
    assertThat(textOf(empty)).doesNotContain(COMPATIBILITY);
  }
}
