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
package org.sonarsource.sonarqube.mcp.tools.agenticreadiness;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertMissingRequiredArgument;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertToolExecutionError;

class GetAgenticReadinessAssessmentToolTests {

  private static final Map<String, String> ORG_ENV = Map.of("SONARQUBE_ORG", "my-org");
  private static final String ASSESSMENT_ID = "assessment-uuid-1";

  @SonarQubeMcpServerTest
  void it_should_return_a_pending_assessment(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH + "/" + ASSESSMENT_ID))
      .willReturn(okJson("""
        {
          "id": "assessment-uuid-1",
          "projectId": "project-uuid-123",
          "createdAt": "2026-06-23T10:00:00Z",
          "status": "PENDING"
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(GetAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("assessmentId", ASSESSMENT_ID));

    assertResultEquals(result, """
      {
        "assessmentId" : "assessment-uuid-1",
        "status" : "PENDING"
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_a_completed_assessment_with_pillars(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH + "/" + ASSESSMENT_ID))
      .willReturn(okJson("""
        {
          "id": "assessment-uuid-1",
          "projectId": "project-uuid-123",
          "createdAt": "2026-06-23T10:00:00Z",
          "status": "COMPLETED",
          "result": {
            "overallLevel": "L2",
            "message": "Project is moderately ready for agents."
          },
          "pillarExecutions": [
            {
              "id": "pe-1",
              "pillarId": "documentation",
              "pillarName": "Documentation & Context",
              "pillarNumber": 1,
              "status": "COMPLETED",
              "level": "L2",
              "subSignals": {
                "readme": {
                  "level": "L2",
                  "evidence": [
                    {"text": "README.md found", "type": "info"},
                    {"text": "Missing architecture diagram", "type": "blocker"}
                  ]
                }
              }
            }
          ]
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(GetAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("assessmentId", ASSESSMENT_ID));

    assertResultEquals(result, """
      {
        "assessmentId" : "assessment-uuid-1",
        "status" : "COMPLETED",
        "overallLevel" : "L2",
        "message" : "Project is moderately ready for agents.",
        "pillars" : [ {
          "name" : "Documentation & Context",
          "number" : 1,
          "level" : "L2",
          "subSignals" : [ {
            "name" : "readme",
            "level" : "L2",
            "evidence" : [ {
              "text" : "README.md found",
              "type" : "info"
            }, {
              "text" : "Missing architecture diagram",
              "type" : "blocker"
            } ]
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_include_actions_in_pillar_executions(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH + "/" + ASSESSMENT_ID))
      .willReturn(okJson("""
        {
          "id": "assessment-uuid-1",
          "projectId": "project-uuid-123",
          "createdAt": "2026-06-23T10:00:00Z",
          "status": "COMPLETED",
          "result": {"overallLevel": "L1"},
          "pillarExecutions": [
            {
              "id": "pe-1",
              "pillarId": "documentation",
              "pillarName": "Documentation & Context",
              "pillarNumber": 1,
              "status": "COMPLETED",
              "level": "L1",
              "actions": [
                {"text": "Add a README.md describing the project purpose"},
                {"text": "Document the architecture in an ADR"}
              ]
            }
          ]
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(GetAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("assessmentId", ASSESSMENT_ID));

    assertResultEquals(result, """
      {
        "assessmentId" : "assessment-uuid-1",
        "status" : "COMPLETED",
        "overallLevel" : "L1",
        "pillars" : [ {
          "name" : "Documentation & Context",
          "number" : 1,
          "level" : "L1",
          "actions" : [ "Add a README.md describing the project purpose", "Document the architecture in an ADR" ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_when_assessment_id_missing(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    var mcpClient = harness.newClient(ORG_ENV);

    assertMissingRequiredArgument(mcpClient.callTool(GetAgenticReadinessAssessmentTool.TOOL_NAME, Map.of()), "assessmentId");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_on_not_found(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH + "/" + ASSESSMENT_ID))
      .willReturn(aResponse().withStatus(404)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(GetAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("assessmentId", ASSESSMENT_ID));

    assertToolExecutionError(result, "404");
  }
}
