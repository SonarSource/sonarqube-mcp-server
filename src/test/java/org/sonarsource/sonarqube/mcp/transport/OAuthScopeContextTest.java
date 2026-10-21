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
package org.sonarsource.sonarqube.mcp.transport;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.tools.ToolAccessMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.READ;
import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.READ_WRITE;
import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.WRITE;

class OAuthScopeContextTest {
  @Test
  void it_should_keep_verified_grants_independent_of_caller_mutations_and_denials() {
    var grants = new HashSet<>(Set.of("read:all"));
    var context = new OAuthScopeContext(grants);
    grants.add("write:all");
    context.deny(WRITE);

    assertThat(context.permits(READ)).isTrue();
    assertThat(context.permits(WRITE)).isFalse();
    assertThat(context.permits(READ_WRITE)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "read:all, READ, true",
    "read:all, WRITE, false",
    "read:all, READ_WRITE, false",
    "write:all, READ, false",
    "write:all, WRITE, true",
    "write:all, READ_WRITE, false",
    "read:all write:all, READ, true",
    "read:all write:all, WRITE, true",
    "read:all write:all, READ_WRITE, true",
    "'', READ, false",
    "'', WRITE, false",
    "'', READ_WRITE, false"
  })
  void it_should_require_the_cloud_scopes_for_each_access_mode(String granted, ToolAccessMode accessMode, boolean permitted) {
    var context = new OAuthScopeContext(granted.isEmpty() ? Set.of() : Set.of(granted.split(" ")));

    assertThat(context.permits(accessMode)).isEqualTo(permitted);
  }

  @Test
  void it_should_publish_immutable_snapshots_of_required_scopes() {
    var context = new OAuthScopeContext(Set.of());
    assertThat(context.requiredScopes()).isEmpty();

    context.deny(READ);
    var snapshot = context.requiredScopes();
    assertThat(context.requiredScopes()).containsExactly(OAuthScope.READ);

    context.deny(WRITE);
    assertThat(context.requiredScopes()).containsExactly(OAuthScope.WRITE);
    assertThat(snapshot).containsExactly(OAuthScope.READ);
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> snapshot.add(OAuthScope.WRITE));
  }
}
