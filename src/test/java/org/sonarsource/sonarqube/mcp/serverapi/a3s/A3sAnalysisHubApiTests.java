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
package org.sonarsource.sonarqube.mcp.serverapi.a3s;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A3sAnalysisHubApiTests {

  private static final String USER_AGENT = "SonarQube MCP tests";
  private ServerApiHelper serverApiHelper;

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @BeforeAll
  void init() {
    var httpClientProvider = new HttpClientProvider(USER_AGENT);
    var httpClient = httpClientProvider.getHttpClient("token");
    serverApiHelper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), "org"), httpClient);
  }

  @Test
  void should_return_mock_response_when_mock_enabled() {
    var api = new A3sAnalysisHubApi(serverApiHelper, true);
    var request = new AnalysisCreationRequest(
      null, "my-org",
      null, "my-project",
      null, "main",
      "src/Main.java", "class Main {}",
      null, "MAIN"
    );

    var response = api.analyze(request);

    assertThat(response.id()).isEqualTo("57f08a8b-4a6e-4c64-bf72-83a892472f22");
    assertThat(response.issues()).hasSize(2);
    assertThat(response.patchResult()).isNull();
  }

  @Test
  void should_return_mock_response_with_patch_result_when_patch_provided() {
    var api = new A3sAnalysisHubApi(serverApiHelper, true);
    var request = new AnalysisCreationRequest(
      null, "my-org",
      null, "my-project",
      null, "main",
      "src/Main.java", "class Main {}",
      "++class Main2 {}", "MAIN"
    );

    var response = api.analyze(request);

    assertThat(response.id()).isEqualTo("57f08a8b-4a6e-4c64-bf72-83a892472f22");
    assertThat(response.issues()).hasSize(2);
    assertThat(response.patchResult()).isNotNull();
    assertThat(response.patchResult().newIssues()).hasSize(1);
    assertThat(response.patchResult().matchedIssues()).hasSize(1);
    assertThat(response.patchResult().closedIssues()).hasSize(1);
  }

  @Test
  void mock_response_should_include_flows() {
    var api = new A3sAnalysisHubApi(serverApiHelper, true);
    var request = new AnalysisCreationRequest(
      null, "my-org",
      null, "my-project",
      null, null,
      "src/Main.java", "class Main {}",
      null, null
    );

    var response = api.analyze(request);

    var issueWithFlows = response.issues().get(0);
    assertThat(issueWithFlows.flows())
      .isNotNull()
      .hasSize(2);

    var dataFlow = issueWithFlows.flows().stream()
      .filter(f -> "DATA".equals(f.type()))
      .findFirst()
      .orElseThrow();
    assertThat(dataFlow.description()).isNotNull();
    assertThat(dataFlow.locations()).hasSize(2);

    var executionFlow = issueWithFlows.flows().stream()
      .filter(f -> "EXECUTION".equals(f.type()))
      .findFirst()
      .orElseThrow();
    assertThat(executionFlow.locations()).hasSize(3);
  }

  @Test
  void mock_response_should_include_text_ranges_with_offsets() {
    var api = new A3sAnalysisHubApi(serverApiHelper, true);
    var request = new AnalysisCreationRequest(
      null, "my-org",
      null, "my-project",
      null, null,
      "src/Main.java", "class Main {}",
      null, null
    );

    var response = api.analyze(request);

    var issue = response.issues().get(0);
    var textRange = issue.textRange();
    assertThat(textRange).isNotNull();
    assertThat(textRange.startLine()).isEqualTo(10);
    assertThat(textRange.endLine()).isEqualTo(15);
    assertThat(textRange.startOffset()).isEqualTo(4);
    assertThat(textRange.endOffset()).isEqualTo(25);
  }

  @Test
  void should_call_real_api_when_mock_disabled() {
    var responseJson = """
      {
        "id": "real-analysis-id",
        "issues": [],
        "patchResult": null
      }
      """;
    sonarqubeMock.stubFor(post(A3sAnalysisHubApi.ANALYSES_PATH)
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_OK)
        .withHeader("Content-Type", "application/json")
        .withBody(responseJson)));

    var api = new A3sAnalysisHubApi(serverApiHelper, false);
    var request = new AnalysisCreationRequest(
      null, "my-org",
      null, "my-project",
      null, null,
      "src/Main.java", "class Main {}",
      null, null
    );

    var response = api.analyze(request);

    assertThat(response.id()).isEqualTo("real-analysis-id");
    assertThat(response.issues()).isEmpty();

    sonarqubeMock.verify(postRequestedFor(urlEqualTo(A3sAnalysisHubApi.ANALYSES_PATH))
      .withHeader("Content-Type", equalTo("application/json")));
  }

}
