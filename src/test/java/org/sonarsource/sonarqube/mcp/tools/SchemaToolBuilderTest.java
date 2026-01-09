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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaToolBuilderTest {

  @Test
  void it_should_include_type_string_in_enum_property_items() {
    var builder = new SchemaToolBuilder(Map.of())
      .setName("test_tool")
      .setTitle("Test Tool")
      .setDescription("Description")
      .addEnumProperty("status", new String[]{"a", "b"}, "Status description");

    var tool = builder.build();
    var properties = tool.inputSchema().properties();
    
    assertThat(properties).containsKey("status");
    
    var status = (Map<String, Object>) properties.get("status");
    assertThat(status).containsEntry("type", "array");
    
    var items = (Map<String, Object>) status.get("items");
    assertThat(items).containsKey("enum");
    assertThat((String[]) items.get("enum")).containsExactly("a", "b");
    
    // This assertion is expected to fail before the fix
    assertThat(items).containsEntry("type", "string");
  }
}
