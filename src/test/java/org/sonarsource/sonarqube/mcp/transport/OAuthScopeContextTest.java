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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class OAuthScopeContextTest {
  @Test
  void it_should_keep_verified_grants_independent_of_caller_mutations_and_denials() {
    var grants = new HashSet<>(Set.of("read:all"));
    var context = new OAuthScopeContext(grants);
    grants.add("write:all");
    context.deny(Set.of("write:all"));

    assertThat(context.permits(Set.of("read:all"))).isTrue();
    assertThat(context.permits(Set.of("write:all"))).isFalse();
    assertThat(context.permits(Set.of("read:all", "write:all"))).isFalse();
  }

  @Test
  void it_should_publish_immutable_snapshots_of_required_scopes() {
    var context = new OAuthScopeContext(Set.of());
    var required = new HashSet<>(Set.of("read:all"));
    assertThat(context.requiredScopes()).isEmpty();

    context.deny(required);
    var snapshot = context.requiredScopes();
    required.add("write:all");
    assertThat(context.requiredScopes()).containsExactly("read:all");

    context.deny(Set.of("write:all"));
    assertThat(context.requiredScopes()).containsExactly("write:all");
    assertThat(snapshot).containsExactly("read:all");
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> snapshot.add("write:all"));
  }
}
