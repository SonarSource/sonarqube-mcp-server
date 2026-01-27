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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ToolNameValidatorTest {

  @Test
  void validate_should_accept_exactly_64_characters() {
    var toolName = "a".repeat(64);
    assertThatCode(() -> ToolNameValidator.validate(toolName))
      .doesNotThrowAnyException();
  }

  @Test
  void validate_should_accept_exactly_1_character() {
    assertThatCode(() -> ToolNameValidator.validate("a"))
      .doesNotThrowAnyException();
  }

  @Test
  void validate_should_be_case_sensitive() {
    assertThatCode(() -> ToolNameValidator.validate("getUser"))
      .doesNotThrowAnyException();
    
    assertThatCode(() -> ToolNameValidator.validate("GetUser"))
      .doesNotThrowAnyException();
    
    assertThatCode(() -> ToolNameValidator.validate("GETUSER"))
      .doesNotThrowAnyException();
  }

  @Test
  void validate_should_accept_sep986_example_names() {
    assertThatCode(() -> ToolNameValidator.validate("getUser"))
      .doesNotThrowAnyException();
    
    assertThatCode(() -> ToolNameValidator.validate("user-profile/update"))
      .doesNotThrowAnyException();
    
    assertThatCode(() -> ToolNameValidator.validate("DATA_EXPORT_v2"))
      .doesNotThrowAnyException();
    
    assertThatCode(() -> ToolNameValidator.validate("admin.tools.list"))
      .doesNotThrowAnyException();
  }

}
