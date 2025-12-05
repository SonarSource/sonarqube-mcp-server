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
package org.sonarsource.sonarqube.mcp.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTokenStoreTest {

  private SessionTokenStore store;

  @BeforeEach
  void setUp() {
    store = SessionTokenStore.getInstance();
    store.clear();
  }

  @Test
  void should_store_new_session_token() {
    var result = store.setTokenIfValid("session-1", "token-abc");

    assertThat(result).isTrue();
    assertThat(store.getToken("session-1")).isEqualTo("token-abc");
    assertThat(store.size()).isEqualTo(1);
  }

  @Test
  void should_accept_same_token_for_existing_session() {
    store.setTokenIfValid("session-1", "token-abc");

    var result = store.setTokenIfValid("session-1", "token-abc");

    assertThat(result).isTrue();
    assertThat(store.getToken("session-1")).isEqualTo("token-abc");
  }

  @Test
  void should_reject_different_token_for_existing_session() {
    store.setTokenIfValid("session-1", "token-abc");

    // Hijacking attempt: different token trying to use same session
    var result = store.setTokenIfValid("session-1", "token-HIJACKED");

    assertThat(result).isFalse();
    // Original token should still be stored
    assertThat(store.getToken("session-1")).isEqualTo("token-abc");
  }

  @Test
  void should_return_null_for_nonexistent_session() {
    assertThat(store.getToken("nonexistent-session")).isNull();
  }

  @Test
  void should_throw_on_null_session_id() {
    assertThrows(NullPointerException.class, () -> store.getToken(null));
  }

  @Test
  void should_store_multiple_sessions() {
    store.setTokenIfValid("session-1", "token-1");
    store.setTokenIfValid("session-2", "token-2");
    store.setTokenIfValid("session-3", "token-3");

    assertThat(store.size()).isEqualTo(3);
    assertThat(store.getToken("session-1")).isEqualTo("token-1");
    assertThat(store.getToken("session-2")).isEqualTo("token-2");
    assertThat(store.getToken("session-3")).isEqualTo("token-3");
  }

  @Test
  void should_clear_all_sessions() {
    store.setTokenIfValid("session-1", "token-1");
    store.setTokenIfValid("session-2", "token-2");

    store.clear();

    assertThat(store.size()).isZero();
    assertThat(store.getToken("session-1")).isNull();
    assertThat(store.getToken("session-2")).isNull();
  }

  @Test
  void should_return_singleton_instance() {
    var instance1 = SessionTokenStore.getInstance();
    var instance2 = SessionTokenStore.getInstance();

    assertThat(instance1).isSameAs(instance2);
  }

  @Test
  void should_isolate_sessions_from_each_other() {
    // Two different users with different sessions and tokens
    store.setTokenIfValid("user-alice-session", "alice-token");
    store.setTokenIfValid("user-bob-session", "bob-token");

    var aliceHijackAttempt = store.setTokenIfValid("user-bob-session", "alice-token");
    assertThat(aliceHijackAttempt).isFalse();

    var bobHijackAttempt = store.setTokenIfValid("user-alice-session", "bob-token");
    assertThat(bobHijackAttempt).isFalse();

    // Original tokens are preserved
    assertThat(store.getToken("user-alice-session")).isEqualTo("alice-token");
    assertThat(store.getToken("user-bob-session")).isEqualTo("bob-token");
  }

  @Test
  void should_shutdown_and_clear_sessions() {
    store.setTokenIfValid("session-1", "token-1");
    store.setTokenIfValid("session-2", "token-2");
    assertThat(store.size()).isEqualTo(2);

    store.shutdown();

    assertThat(store.size()).isZero();
    assertThat(store.getToken("session-1")).isNull();
    assertThat(store.getToken("session-2")).isNull();
  }

}

