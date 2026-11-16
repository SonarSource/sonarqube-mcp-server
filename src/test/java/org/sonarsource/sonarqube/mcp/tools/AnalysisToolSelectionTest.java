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
package org.sonarsource.sonarqube.mcp.tools;

import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalysisToolSelectionTest {

  private final McpServerLaunchConfiguration configuration = mock(McpServerLaunchConfiguration.class);
  private final BackendService backend = mock(BackendService.class);
  private final SonarQubeIdeBridgeClient bridge = mock(SonarQubeIdeBridgeClient.class);
  private final Supplier<SonarQubeIdeBridgeClient> bridgeFactory = () -> bridge;

  @ParameterizedTest
  @CsvSource({"false,false,NONE", "false,true,LOCAL", "true,false,NONE", "true,true,LOCAL"})
  void it_should_require_a_startup_token_for_http_analysis_even_when_local_is_forced(boolean cloud, boolean token, AnalysisToolSelection.Mode expected) {
    when(configuration.isHttpEnabled()).thenReturn(true);
    when(configuration.isSonarQubeCloud()).thenReturn(cloud);
    when(configuration.isForceLocalAnalysis()).thenReturn(true);
    when(configuration.getSonarQubeToken()).thenReturn(token ? "startup-token" : null);
    when(configuration.getSonarQubeIdePort()).thenReturn(64120);

    assertThat(AnalysisToolSelection.resolve(configuration, availability(false), backend, bridgeFactory).mode()).isEqualTo(expected);
    verifyNoInteractions(backend, bridge);
  }

  @Test
  void it_should_not_fall_back_when_vortex_is_selected_without_a_workspace() {
    when(configuration.getSonarQubeIdePort()).thenReturn(64120);

    assertThat(AnalysisToolSelection.resolve(configuration, availability(true), backend, bridgeFactory).mode()).isEqualTo(AnalysisToolSelection.Mode.NONE);
    verifyNoInteractions(backend, bridge);
  }

  @Test
  void it_should_prefer_vortex_to_an_available_ide_bridge() {
    when(configuration.getWorkspacePath()).thenReturn(Path.of("workspace"));
    when(configuration.getSonarQubeIdePort()).thenReturn(64120);

    assertThat(AnalysisToolSelection.resolve(configuration, availability(true), backend, bridgeFactory).mode()).isEqualTo(AnalysisToolSelection.Mode.VORTEX);
    verifyNoInteractions(backend, bridge);
  }

  @ParameterizedTest
  @CsvSource({"true,false,true,IDE", "true,true,true,LOCAL", "true,false,false,LOCAL", "false,true,true,IDE"})
  void it_should_select_an_available_bridge_unless_cloud_local_analysis_is_forced(boolean cloud, boolean forceLocal, boolean available,
    AnalysisToolSelection.Mode expected) {
    when(configuration.isSonarQubeCloud()).thenReturn(cloud);
    when(configuration.isForceLocalAnalysis()).thenReturn(forceLocal);
    when(configuration.getSonarQubeIdePort()).thenReturn(64120);
    when(bridge.isAvailable()).thenReturn(available);

    var result = AnalysisToolSelection.resolve(configuration, availability(false), backend, bridgeFactory);

    assertThat(result.mode()).isEqualTo(expected);
    if (expected == AnalysisToolSelection.Mode.IDE) {
      assertThat(result.ideBridge()).isSameAs(bridge);
      verify(backend).notifySonarQubeIdeIntegration();
    } else {
      assertThat(result.ideBridge()).isNull();
      verifyNoInteractions(backend);
    }
    if (cloud && forceLocal) {
      verifyNoInteractions(bridge);
    } else {
      verify(bridge).isAvailable();
    }
  }

  private ToolAvailability.StartupFacts availability(boolean vortexAnalysis) {
    return new ToolAvailability.StartupFacts(configuration.isHttpEnabled() ? TransportMode.HTTP : TransportMode.STDIO,
      configuration.isSonarQubeCloud(), false, false, false, false, vortexAnalysis);
  }
}
