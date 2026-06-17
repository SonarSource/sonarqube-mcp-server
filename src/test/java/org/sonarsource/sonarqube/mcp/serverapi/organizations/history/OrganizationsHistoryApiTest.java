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
package org.sonarsource.sonarqube.mcp.serverapi.organizations.history;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationsHistoryApiTest {

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private OrganizationsHistoryApi organizationsHistoryApi;

  @BeforeEach
  void init() {
    var httpClient = new HttpClientProvider("test").getHttpClient("token");
    var helper = new ServerApiHelper(new EndpointParams("https://sonarcloud.io", "my-org", sonarqubeMock.baseUrl(), true), httpClient);
    organizationsHistoryApi = new OrganizationsHistoryApi(helper);
  }

  @Test
  void getMeasuresHistory_should_serialize_query_params() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsHistoryApi.MEASURES_HISTORY_PATH))
      .willReturn(jsonResponse("""
        {"measuresHistory":[]}
        """, 200)));

    organizationsHistoryApi.getMeasuresHistory("PROJECT_BRANCH", "project-id", List.of("ncloc", "coverage"), "2024-01-01", null);

    sonarqubeMock.verify(getRequestedFor(urlEqualTo(
      "/organizations/measures-history?entityType=PROJECT_BRANCH&entityId=project-id&metricKeys=ncloc,coverage&startDate=2024-01-01")));
  }

  @Test
  void getMeasuresHistory_should_parse_response() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsHistoryApi.MEASURES_HISTORY_PATH))
      .willReturn(jsonResponse("""
        {
          "measuresHistory": [
            {
              "date": "2024-01-01",
              "measures": [
                {"metric": "ncloc", "type": "INT", "value": "42"},
                {"metric": "coverage", "type": "PERCENT", "value": "87.5"},
                {"metric": "bugs", "value": "0"}
              ]
            }
          ]
        }
        """, 200)));

    var response = organizationsHistoryApi.getMeasuresHistory("PROJECT_BRANCH", "project-id", List.of("ncloc", "coverage"), "2024-01-01", "2024-01-31");

    assertThat(response.measuresHistory()).hasSize(1);
    var history = response.measuresHistory().get(0);
    assertThat(history.date()).isEqualTo("2024-01-01");
    assertThat(history.measures())
      .extracting("metric", "type", "value")
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("ncloc", "INT", "42"),
        org.assertj.core.groups.Tuple.tuple("coverage", "PERCENT", "87.5"),
        org.assertj.core.groups.Tuple.tuple("bugs", null, "0"));
  }

  @Test
  void getIssueCountHistory_should_serialize_query_params() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsHistoryApi.ISSUE_COUNT_HISTORY_PATH))
      .willReturn(jsonResponse("""
        {"issueCountHistory":[]}
        """, 200)));

    organizationsHistoryApi.getIssueCountHistory("project-id", "PROJECT_BRANCH", "2024-01-01", "2024-01-31",
      List.of("MAINTAINABILITY:HIGH", "SECURITY:LOW"), List.of("BUG", "VULNERABILITY"),
      List.of("java:S106", "javascript:S3776"), List.of("HIGH", "MEDIUM"), "STATUS", List.of("OPEN", "CONFIRMED"));

    sonarqubeMock.verify(getRequestedFor(urlEqualTo(
      "/organizations/issue-count-history?entityId=project-id&entityType=PROJECT_BRANCH&startDate=2024-01-01&endDate=2024-01-31" +
        "&impacts=MAINTAINABILITY%3AHIGH,SECURITY%3ALOW&issueTypes=BUG,VULNERABILITY" +
        "&ruleKeys=java%3AS106,javascript%3AS3776&severities=HIGH,MEDIUM&sliceBy=STATUS&statuses=OPEN,CONFIRMED")));
  }

  @Test
  void getIssueCountHistory_should_parse_response() {
    sonarqubeMock.stubFor(get(urlPathEqualTo(OrganizationsHistoryApi.ISSUE_COUNT_HISTORY_PATH))
      .willReturn(jsonResponse("""
        {
          "issueCountHistory": [
            {
              "date": "2024-01-01",
              "distribution": [
                {"key": "OPEN", "value": 7},
                {"key": "CONFIRMED", "value": 3}
              ]
            }
          ]
        }
        """, 200)));

    var response = organizationsHistoryApi.getIssueCountHistory("project-id", "PROJECT_BRANCH", "2024-01-01", null,
      null, null, null, null, null, null);

    assertThat(response.issueCountHistory()).hasSize(1);
    var history = response.issueCountHistory().get(0);
    assertThat(history.date()).isEqualTo("2024-01-01");
    assertThat(history.distribution())
      .extracting("key", "value")
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("OPEN", 7),
        org.assertj.core.groups.Tuple.tuple("CONFIRMED", 3));
  }

}
