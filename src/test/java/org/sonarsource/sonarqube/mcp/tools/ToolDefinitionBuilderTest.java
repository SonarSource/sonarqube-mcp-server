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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class ToolDefinitionBuilderTest {

  @Test
  void it_should_exclude_organization_from_standard_tool_schemas() {
    var definition = ToolDefinitionBuilder.builder()
      .setName("standard_tool")
      .setDescription("Standard tool")
      .addRequiredStringProperty(ToolParameters.PROJECT_KEY, "Project key")
      .build();

    assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP)
      .containsKey(ToolParameters.PROJECT_KEY)
      .doesNotContainKey(ToolParameters.ORGANIZATION);
    assertThat(definition.inputSchema().get("required")).asInstanceOf(LIST).containsExactly(ToolParameters.PROJECT_KEY);
  }

  @Test
  void it_should_include_optional_organization_in_cloud_tool_schemas() {
    var definition = ToolDefinitionBuilder.cloudBuilder()
      .setName("cloud_tool")
      .setDescription("Cloud tool")
      .addRequiredStringProperty(ToolParameters.PROJECT_KEY, "Project key")
      .build();

    assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP)
      .containsKey(ToolParameters.PROJECT_KEY)
      .containsEntry(ToolParameters.ORGANIZATION, Map.of(
        "type", "string",
        "description", "Organization key. Defaults to the request header or configured organization; call list_organizations if ambiguous."));
    assertThat(definition.inputSchema().get("required")).asInstanceOf(LIST).containsExactly(ToolParameters.PROJECT_KEY);
  }
}
