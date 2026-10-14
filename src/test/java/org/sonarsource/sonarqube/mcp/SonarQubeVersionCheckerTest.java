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
package org.sonarsource.sonarqube.mcp;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

  @ParameterizedTest
  @ValueSource(strings = {"2025.1", "2025.4", "25.1", "25.2"})
  void it_should_not_throw_if_sonarqube_server_version_is_supported(String version) {
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", version, "UP"));

    assertThatCode(versionChecker::failIfSonarQubeServerVersionIsNotSupported)
      .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"9.9.1", "10.4", "24.12", "25.0"})
  void it_should_throw_if_sonarqube_server_version_is_not_supported(String version) {
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", version, "UP"));

    var throwable = catchThrowable(versionChecker::failIfSonarQubeServerVersionIsNotSupported);
    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage(SonarQubeVersionChecker.UNSUPPORTED_SERVER_VERSION_MESSAGE);
    verify(systemApi).getStatus();
  }

  @Test
  void it_should_recover_when_sonarqube_server_becomes_available() {
    when(systemApi.getStatus())
      .thenThrow(new CompletionException(new ConnectException("Connection refused")))
      .thenReturn(new StatusResponse("id", "2025.1", "UP"));

    assertThatCode(versionChecker::failIfSonarQubeServerVersionIsNotSupported)
      .doesNotThrowAnyException();
    verify(systemApi, times(2)).getStatus();
  }

  @Test
  void it_should_report_a_clear_error_when_sonarqube_server_stays_unavailable() {
    when(systemApi.getStatus()).thenThrow(new ServerInternalErrorException("Error 503"));

    var throwable = catchThrowable(versionChecker::failIfSonarQubeServerVersionIsNotSupported);

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("unavailable after 3 attempts")
      .hasMessageContaining("wait for SonarQube to become healthy")
      .hasCauseInstanceOf(ServerInternalErrorException.class);
    verify(systemApi, times(3)).getStatus();
  }

  @Test
  void it_should_report_tls_failure_without_retrying() {
    when(systemApi.getStatus()).thenThrow(new CompletionException(new SSLHandshakeException("PKIX path building failed")));

    var throwable = catchThrowable(versionChecker::failIfSonarQubeServerVersionIsNotSupported);

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SSLHandshakeException: PKIX path building failed")
      .hasMessageContaining("Check SONARQUBE_URL and its connection settings");
    verify(systemApi).getStatus();
  }

  @Test
  void it_should_report_timeout_without_retrying() {
    when(systemApi.getStatus()).thenThrow(new CompletionException(new HttpTimeoutException("request timed out")));

    var throwable = catchThrowable(versionChecker::failIfSonarQubeServerVersionIsNotSupported);

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("HttpTimeoutException: request timed out");
    verify(systemApi).getStatus();
  }

  @Test
  void it_should_return_false_for_sonarqube_cloud() {
    when(serverApi.isSonarQubeCloud()).thenReturn(true);

    var result = versionChecker.isSonarQubeServerVersionHigherOrEqualsThan("2025.3");

    assertThat(result).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025.4", "2025.3", "10.9.1", "2025.3-SNAPSHOT"})
  void it_should_return_true_when_valid(String input) {
    when(serverApi.isSonarQubeCloud()).thenReturn(false);
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", "2025.4", "UP"));

    var result = versionChecker.isSonarQubeServerVersionHigherOrEqualsThan(input);

    assertThat(result).isTrue();
  }

  @Test
  void it_should_return_false_when_server_version_is_lower_than_min_version() {
    when(serverApi.isSonarQubeCloud()).thenReturn(false);
    when(systemApi.getStatus()).thenReturn(new StatusResponse("id", "2025.2", "UP"));

    var result = versionChecker.isSonarQubeServerVersionHigherOrEqualsThan("2025.3");

    assertThat(result).isFalse();
  }

}
