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
package org.sonarsource.sonarqube.mcp.tools.groups;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GroupToolValidationTest {
  private static final String ORGANIZATION_ID = "b2c3d4e5-f6a7-4901-bcde-f12345678901";

  @Test
  void it_should_reject_invalid_group_and_organization_ids_before_api_calls() {
    var provider = mock(ServerApiProvider.class);
    assertThatThrownBy(() -> new DeleteGroupTool(provider).execute(new Tool.Arguments(Map.of("groupId", "../../other"), null)))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ListGroupsTool(provider).execute(new Tool.Arguments(Map.of("organizationId", "not-a-uuid"), null)))
      .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CreateGroupTool(provider).execute(new Tool.Arguments(Map.of("organizationId", "not-a-uuid", "name", "fixture"), null)))
      .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(provider);
  }

  @Test
  void it_should_reject_invalid_group_field_lengths_before_mutation() {
    var provider = mock(ServerApiProvider.class);
    assertThat(new CreateGroupTool(provider).execute(new Tool.Arguments(Map.of("organizationId", ORGANIZATION_ID, "name", "a".repeat(256)), null)).isError()).isTrue();
    assertThat(new CreateGroupTool(provider).execute(new Tool.Arguments(Map.of("organizationId", ORGANIZATION_ID, "name", "fixture", "description", "a".repeat(201)), null)).isError()).isTrue();
    verifyNoInteractions(provider);
  }

  @Test
  void it_should_reject_invalid_pagination_before_api_calls() {
    var provider = mock(ServerApiProvider.class);
    for (var pagination : java.util.List.of(Map.of("pageIndex", 0), Map.of("pageSize", 0), Map.of("pageSize", 501))) {
      var arguments = new java.util.HashMap<String, Object>(pagination);
      arguments.put("organizationId", ORGANIZATION_ID);
      assertThat(new ListGroupsTool(provider).execute(new Tool.Arguments(arguments, null)).isError()).isTrue();
    }
    verifyNoInteractions(provider);
  }
}

