/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

public class AnalysisToolTests {

  @Nested
  class MissingPrerequisite {
    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_codeSnippet_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        AnalysisTool.TOOL_NAME,
        Map.of(AnalysisTool.LANGUAGE_PROPERTY, ""));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: codeSnippet", true));
    }
  }

  @Nested
  class Connected {

    @SonarQubeMcpServerTest
    void it_should_find_no_issues_in_an_empty_file(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.SNIPPET_PROPERTY, "",
          AnalysisTool.LANGUAGE_PROPERTY, ""));

      assertResultEquals(result, "{\"issues\":[],\"issueCount\":0}");
    }

    @SonarQubeMcpServerTest
    void it_should_find_an_issues_in_a_php_file_when_rule_enabled_in_default_quality_profile(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.SNIPPET_PROPERTY, """
            // TODO just do it
            """,
          AnalysisTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "ruleKey" : "php:S1135",
            "primaryMessage" : "Complete the task associated to this \\"TODO\\" comment.",
            "severity" : "INFO",
            "cleanCodeAttribute" : "COMPLETE",
            "impacts" : "{MAINTAINABILITY=INFO}",
            "hasQuickFixes" : false,
            "textRange" : {
              "startLine" : 1,
              "endLine" : 1
            }
          } ],
          "issueCount" : 1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_find_an_issues_in_a_php_file_when_rule_enabled_in_project_quality_profile(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, "projectKey", List.of("php:S1135"));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.PROJECT_KEY_PROPERTY, "projectKey",
          AnalysisTool.SNIPPET_PROPERTY, """
            // TODO just do it
            """,
          AnalysisTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, """
        {
           "issues":[
              {
                 "ruleKey":"php:S1135",
                 "primaryMessage":"Complete the task associated to this \\"TODO\\" comment.",
                 "severity":"INFO",
                 "cleanCodeAttribute":"COMPLETE",
                 "impacts":"{MAINTAINABILITY=INFO}",
                 "hasQuickFixes":false,
                 "textRange":{
                    "startLine":1,
                    "endLine":1
                 }
              }
           ],
           "issueCount":1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_find_no_issues_if_rule_is_not_active(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of());
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.SNIPPET_PROPERTY, """
            // TODO just do it
            """,
          AnalysisTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, "{\"issues\":[],\"issueCount\":0}");
    }
  }

  private void mockServerRules(SonarQubeMcpServerTestHarness harness, @Nullable String projectKey, List<String> activeRuleKeys) {
    mockQualityProfiles(harness, projectKey, "qpKey");
    mockRules(harness, "qpKey", activeRuleKeys);
  }

  private static void mockQualityProfiles(SonarQubeMcpServerTestHarness harness, @Nullable String projectKey, String qualityProfileKey) {
    var query = projectKey == null ? "defaults=true" : ("project=" + projectKey);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?" + query).willReturn(okJson("""
      {
          "profiles": [
            {
              "key": "%s"
            }
          ]
        }
      """.formatted(qualityProfileKey))));
  }

  private static void mockRules(SonarQubeMcpServerTestHarness harness, String qualityProfileKey, List<String> activeRuleKeys) {
    var rulesPayload = activeRuleKeys.stream().map("""
      "%s": [
        {
          "params": []
        }
      ]
      """::formatted).collect(Collectors.joining(","));

    harness.getMockSonarQubeServer().stubFor(get(RulesApi.SEARCH_PATH + "?qprofile=" + qualityProfileKey + "&activation=true&f=templateKey%2Cactives&p=1").willReturn(okJson(
      """
        {
          "actives": {
            %s
          }
        }
        """.formatted(rulesPayload))));
  }

}
