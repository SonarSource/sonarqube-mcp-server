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
package org.sonarsource.sonarqube.mcp.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextThreadLocalAccessorTest {

  private RequestContextThreadLocalAccessor accessor;

  @BeforeEach
  void setUp() {
    accessor = new RequestContextThreadLocalAccessor();
    RequestContext.clear();
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  @Test
  void key_should_return_expected_key() {
    assertThat(accessor.key()).isEqualTo(RequestContextThreadLocalAccessor.KEY);
  }

  @Test
  void getValue_should_return_null_when_no_context_set() {
    assertThat(accessor.getValue()).isNull();
  }

  @Test
  void getValue_should_return_current_context() {
    RequestContext.set("test-token");

    assertThat(accessor.getValue()).isNotNull();
    assertThat(accessor.getValue().sonarQubeToken()).isEqualTo("test-token");
  }

  @Test
  void setValue_should_set_context_in_thread_local() {
    var context = new RequestContext("new-token");
    accessor.setValue(context);

    assertThat(RequestContext.current()).isNotNull();
    assertThat(RequestContext.current().sonarQubeToken()).isEqualTo("new-token");
  }

  @Test
  void setValue_with_null_should_not_throw() {
    // Set a context first
    RequestContext.set("existing-token");
    
    // Calling setValue with null should not throw and should not change existing context
    accessor.setValue(null);
    
    // Context should still be there (null values are ignored)
    assertThat(RequestContext.current()).isNotNull();
    assertThat(RequestContext.current().sonarQubeToken()).isEqualTo("existing-token");
  }

  @Test
  void setValueNoArg_should_clear_context() {
    RequestContext.set("token-to-clear");
    assertThat(RequestContext.current()).isNotNull();

    accessor.setValue();

    assertThat(RequestContext.current()).isNull();
  }

}
