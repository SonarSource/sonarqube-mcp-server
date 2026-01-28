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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunAdvancedCodeAnalysisToolTests {

  private ServerApiProvider serverApiProvider;
  private RunAdvancedCodeAnalysisTool tool;

  @BeforeEach
  void setUp() {
    serverApiProvider = mock(ServerApiProvider.class);
    var serverApiHelper = mock(ServerApiHelper.class);
    when(serverApiHelper.getOrganization()).thenReturn("test-org");
    var serverApi = new ServerApi(serverApiHelper, true);
    when(serverApiProvider.get()).thenReturn(serverApi);
    tool = new RunAdvancedCodeAnalysisTool(serverApiProvider);
  }

  @Test
  void should_have_correct_tool_name() {
    assertThat(tool.definition().name()).isEqualTo("run_advanced_code_analysis");
  }

  @Test
  void should_be_in_analysis_category() {
    assertThat(tool.getCategory()).isEqualTo(ToolCategory.ANALYSIS);
  }

  @Test
  void should_be_marked_read_only() {
    assertThat(tool.definition().annotations().readOnlyHint()).isTrue();
  }

  @Test
  void should_have_required_input_properties() {
    var inputSchema = tool.definition().inputSchema();
    var requiredProps = inputSchema.required();

    assertThat(requiredProps).contains("filePath", "fileContent");
  }

  @Test
  void should_have_optional_input_properties() {
    var inputSchema = tool.definition().inputSchema();
    var properties = inputSchema.properties();

    assertThat(properties).containsKeys(
      "organizationId", "organizationKey",
      "projectId", "projectKey",
      "branchId", "branchName",
      "filePath", "fileContent",
      "patchContent", "fileScope"
    );
  }

  @Test
  void should_return_analysis_with_issues_and_flows() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationKey", "my-org",
      "projectKey", "my-project",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
    var content = result.toCallToolResult().content().get(0).toString();
    assertThat(content)
      .contains("issues")
      .contains("java:S2259")
      .contains("flows")
      .contains("DATA")
      .contains("EXECUTION")
      .doesNotContain("analysisId");
  }

  @Test
  void should_return_patch_result_when_patch_content_provided() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationKey", "my-org",
      "projectKey", "my-project",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}",
      "patchContent", "++class MyClass2 {}"
    ));

    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
    var content = result.toCallToolResult().content().get(0).toString();
    assertThat(content)
      .contains("patchResult")
      .contains("newIssues")
      .contains("matchedIssues")
      .contains("closedIssues");
  }

  @Test
  void should_include_text_range_with_line_numbers_only() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationKey", "my-org",
      "projectKey", "my-project",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
    var content = result.toCallToolResult().content().get(0).toString();
    assertThat(content)
      .contains("startLine")
      .contains("endLine")
      .doesNotContain("startOffset")
      .doesNotContain("endOffset");
  }

  @Test
  void should_fail_when_organization_identifier_is_missing() {
    var arguments = new Tool.Arguments(Map.of(
      "projectKey", "my-project",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    assertThatThrownBy(() -> tool.execute(arguments))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Either organizationId or organizationKey is required");
  }

  @Test
  void should_fail_when_project_identifier_is_missing() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationKey", "my-org",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    assertThatThrownBy(() -> tool.execute(arguments))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Either projectId or projectKey is required");
  }

  @Test
  void should_accept_organization_id_instead_of_key() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationId", "org-uuid",
      "projectKey", "my-project",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
  }

  @Test
  void should_accept_project_id_instead_of_key() {
    var arguments = new Tool.Arguments(Map.of(
      "organizationKey", "my-org",
      "projectId", "project-uuid",
      "filePath", "src/main/java/MyClass.java",
      "fileContent", "class MyClass {}"
    ));

    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
  }

}
