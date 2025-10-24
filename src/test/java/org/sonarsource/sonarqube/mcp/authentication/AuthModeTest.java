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
package org.sonarsource.sonarqube.mcp.authentication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthModeTest {

  @Test
  void should_parse_token_mode() {
    assertThat(AuthMode.fromString("token")).isEqualTo(AuthMode.TOKEN);
    assertThat(AuthMode.fromString("TOKEN")).isEqualTo(AuthMode.TOKEN);
    assertThat(AuthMode.fromString("Token")).isEqualTo(AuthMode.TOKEN);
  }

  @Test
  void should_parse_oauth_mode() {
    assertThat(AuthMode.fromString("oauth")).isEqualTo(AuthMode.OAUTH);
    assertThat(AuthMode.fromString("OAUTH")).isEqualTo(AuthMode.OAUTH);
    assertThat(AuthMode.fromString("OAuth")).isEqualTo(AuthMode.OAUTH);
  }

  @Test
  void should_default_to_token_for_null_or_blank() {
    assertThat(AuthMode.fromString(null)).isEqualTo(AuthMode.TOKEN);
    assertThat(AuthMode.fromString("")).isEqualTo(AuthMode.TOKEN);
    assertThat(AuthMode.fromString("  ")).isEqualTo(AuthMode.TOKEN);
  }

  @Test
  void should_throw_for_invalid_mode() {
    assertThatThrownBy(() -> AuthMode.fromString("invalid"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid authentication mode: invalid")
      .hasMessageContaining("TOKEN, OAUTH");
  }

}


