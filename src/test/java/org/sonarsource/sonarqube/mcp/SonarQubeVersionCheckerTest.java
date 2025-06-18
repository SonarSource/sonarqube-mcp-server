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
package org.sonarsource.sonarqube.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarQubeVersionCheckerTest {
  private ServerApi serverApi;
  private SystemApi systemApi;
  private SonarQubeVersionChecker versionChecker;

  @BeforeEach
  void prepare() {
    serverApi = mock(ServerApi.class);
    systemApi = mock(SystemApi.class);
    when(serverApi.systemApi()).thenReturn(systemApi);
    versionChecker = new SonarQubeVersionChecker(serverApi);
  }

  @Test
  void it_should_not_throw_if_sonarqube_cloud() {
    when(serverApi.isSonarQubeCloud()).thenReturn(true);

    assertThatCode(versionChecker::failIfSonarQubeServerVersionIsNotSupported)
      .doesNotThrowAnyException();
  }

  @Test
  void it_should_not_throw_if_sonarqube_server_version_is_supported() {
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", "2025.1", "UP"));

    assertThatCode(versionChecker::failIfSonarQubeServerVersionIsNotSupported)
      .doesNotThrowAnyException();
  }

  @Test
  void it_should_throw_if_sonarqube_server_version_is_not_supported() {
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", "10.4", "UP"));

    var throwable = catchThrowable(versionChecker::failIfSonarQubeServerVersionIsNotSupported);
    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("SonarQube server version is not supported, minimal version is SQS 2025.1 or SQCB 25.1");
  }
}
