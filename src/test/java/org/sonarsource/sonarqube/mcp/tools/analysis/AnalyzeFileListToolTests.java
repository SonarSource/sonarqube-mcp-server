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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool.FILE_ABSOLUTE_PATHS_PROPERTY;

class AnalyzeFileListToolTests {

  private SonarQubeIdeBridgeClient bridgeClient;
  private AnalyzeFileListTool underTest;

  @BeforeEach
  void setUp() {
    bridgeClient = mock(SonarQubeIdeBridgeClient.class);
    underTest = new AnalyzeFileListTool(bridgeClient);
  }

  @Test
  void it_should_validate_annotations() {
    assertThat(underTest.getCategories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.IDE);

    assertThat(underTest.definition().annotations()).isNotNull();
    assertThat(underTest.definition().annotations().readOnlyHint()).isTrue();
    assertThat(underTest.definition().annotations().openWorldHint()).isTrue();
    assertThat(underTest.definition().annotations().idempotentHint()).isFalse();
    assertThat(underTest.definition().annotations().destructiveHint()).isFalse();

    assertThat(underTest.definition().outputSchema()).isNull();
  }

  @Nested
  class WhenBridgeIsNotAvailable {
    @Test
    void it_should_return_failure_when_bridge_is_not_available() {
      when(bridgeClient.isAvailable()).thenReturn(false);

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of("file1.java", "file2.java")
      ), null)).toCallToolResult();

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("SonarQube for IDE is not available. Please ensure SonarQube for IDE is running.").build());
    }
  }

  @Nested
  class WhenBridgeIsAvailable {
    @BeforeEach
    void setUp() {
      when(bridgeClient.isAvailable()).thenReturn(true);
    }

    @Test
    void it_should_return_failure_when_analysis_fails() {
      when(bridgeClient.isAvailable()).thenReturn(true);
      when(bridgeClient.requestAnalyzeFileList(anyList())).thenReturn(Optional.empty());

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of("file1.java")
      ), null)).toCallToolResult();

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("Failed to request analysis of the list of files. Check logs for details.").build());
    }

    @Test
    void it_should_return_success_when_no_issues_found() {
      when(bridgeClient.isAvailable()).thenReturn(true);
      var emptyResponse = new SonarQubeIdeBridgeClient.AnalyzeFileListResponse(List.of());
      when(bridgeClient.requestAnalyzeFileList(anyList())).thenReturn(Optional.of(emptyResponse));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of("file1.java")
      ), null)).toCallToolResult();

      assertResultEquals(result, """
        {
          "findings" : [ ],
          "findingsCount" : 0
        }""");
    }

    @Test
    void it_should_return_success_with_issues_found() {
      when(bridgeClient.isAvailable()).thenReturn(true);
      var textRange = new TextRange(10, 0, 10, 20);
      var issue1 = new SonarQubeIdeBridgeClient.AnalyzeFileListIssueResponse(
        "java:S1234", "Test issue message", "MAJOR", "src/main/java/Test.java", textRange);
      var issue2 = new SonarQubeIdeBridgeClient.AnalyzeFileListIssueResponse(
        "java:S5678", "Another issue", "MINOR", "src/main/java/Another.java", null);
      
      var responseWithIssues = new SonarQubeIdeBridgeClient.AnalyzeFileListResponse(List.of(issue1, issue2));
      when(bridgeClient.requestAnalyzeFileList(anyList())).thenReturn(Optional.of(responseWithIssues));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of("file1.java", "file2.java")
      ), null)).toCallToolResult();

      assertResultEquals(result, """
        {
          "findings" : [ {
            "ruleKey" : "java:S1234",
            "severity" : "MAJOR",
            "message" : "Test issue message",
            "filePath" : "src/main/java/Test.java",
            "textRange" : {
              "startLine" : 10,
              "endLine" : 10
            }
          }, {
            "ruleKey" : "java:S5678",
            "severity" : "MINOR",
            "message" : "Another issue",
            "filePath" : "src/main/java/Another.java"
          } ],
          "findingsCount" : 2
        }""");
    }

    @Test
    void it_should_limit_issues_display_to_100() {
      when(bridgeClient.isAvailable()).thenReturn(true);
      // Create 150 issues
      var issues = new ArrayList<SonarQubeIdeBridgeClient.AnalyzeFileListIssueResponse>();
      for (int i = 0; i < 150; i++) {
        issues.add(new SonarQubeIdeBridgeClient.AnalyzeFileListIssueResponse(
          "java:S" + i, "Issue " + i, "INFO", "file" + i + ".java", null));
      }
      
      var responseWithManyIssues = new SonarQubeIdeBridgeClient.AnalyzeFileListResponse(issues);
      when(bridgeClient.requestAnalyzeFileList(anyList())).thenReturn(Optional.of(responseWithManyIssues));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of("file1.java")
      ), null)).toCallToolResult();

      assertThat(result.isError()).isFalse();
      var json = JsonParser.parseString(((McpSchema.TextContent) result.content().getFirst()).text()).getAsJsonObject();
      assertThat(json.getAsJsonArray("findings")).hasSize(150);
      assertThat(json.get("findingsCount").getAsInt()).isEqualTo(150);
    }

    @Test
    void it_should_handle_empty_file_list() {
      when(bridgeClient.isAvailable()).thenReturn(true);
      var emptyResponse = new SonarQubeIdeBridgeClient.AnalyzeFileListResponse(List.of());
      when(bridgeClient.requestAnalyzeFileList(List.of())).thenReturn(Optional.of(emptyResponse));

      var result = underTest.execute(new Tool.Arguments(Map.of(
        FILE_ABSOLUTE_PATHS_PROPERTY, List.of()
      ), null)).toCallToolResult();

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("No files provided to analyze. Please provide a list of file paths using the '" + FILE_ABSOLUTE_PATHS_PROPERTY + "' property.");
    }
  }
}
