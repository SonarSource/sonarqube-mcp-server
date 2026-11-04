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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ClientErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectureCurrentApiTests {
  private static final String LANGUAGE_BINDING_ERROR = "Method parameter 'language': Failed to convert value of type 'java.lang.String' "
    + "to required type 'com.sonarsource.architecture.publicapi.api.model.PublicEcosystem'";

  @Test
  void it_should_explain_the_server_language_binding_failure_and_keep_its_cause() {
    var helper = mock(ServerApiHelper.class);
    var error = new ClientErrorException(400, LANGUAGE_BINDING_ERROR);
    when(helper.get(anyString())).thenThrow(error);
    var api = new ArchitectureApi(helper);

    assertThatThrownBy(() -> api.getStructure("my_project", "org", "branch", "java"))
      .hasMessageContaining("known SonarQube Server issue", "currently blocks current architecture reads", "HTTP 400")
      .hasMessageNotContaining("PublicEcosystem")
      .hasMessageNotContaining("Failed to convert")
      .hasMessageNotContaining("upgrade")
      .hasMessageNotContaining("JAVA")
      .hasCause(error);
    verify(helper).get("/api/v2/architecture/current-architectures?organizationId=org&branchId=branch&language=java");
  }

  @ParameterizedTest
  @MethodSource("otherCurrentFailures")
  void it_should_preserve_other_current_failures(boolean cloud, RuntimeException error) {
    var helper = mock(ServerApiHelper.class);
    when(helper.isSonarQubeCloud()).thenReturn(cloud);
    if (cloud) {
      when(helper.getApiSubdomain(anyString())).thenThrow(error);
    } else {
      when(helper.get(anyString())).thenThrow(error);
    }
    var api = new ArchitectureApi(helper);

    var thrown = assertThatThrownBy(() -> api.getStructure("my_project", "org", "branch", "java"));
    if (error instanceof ForbiddenException) {
      thrown.isInstanceOf(ForbiddenException.class).hasMessageContaining("Insufficient permissions").hasMessageNotContaining("language-binding");
    } else {
      thrown.isSameAs(error);
    }
  }

  private static Stream<Arguments> otherCurrentFailures() {
    return Stream.of(
      Arguments.of(true, new ClientErrorException(400, LANGUAGE_BINDING_ERROR)),
      Arguments.of(false, new ClientErrorException(400, "Invalid branchId")),
      Arguments.of(false, new ClientErrorException(400, "Missing PublicEcosystem language parameter")),
      Arguments.of(false, new ClientErrorException(400, "Method parameter 'language': Failed to convert another type")),
      Arguments.of(false, new ClientErrorException(408, LANGUAGE_BINDING_ERROR)),
      Arguments.of(false, new UnauthorizedException("Not authorized")),
      Arguments.of(false, new ForbiddenException("Insufficient permissions")),
      Arguments.of(false, new ServerInternalErrorException(LANGUAGE_BINDING_ERROR)));
  }
}
