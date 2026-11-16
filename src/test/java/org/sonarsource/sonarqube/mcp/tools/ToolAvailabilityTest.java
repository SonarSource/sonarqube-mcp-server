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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.serverapi.OrgFeatureEntitlements;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolAvailabilityTest {

  private final McpServerLaunchConfiguration configuration = mock(McpServerLaunchConfiguration.class);
  private final ServerApi api = mock(ServerApi.class);
  private final OrgFeatureEntitlements entitlements = mock(OrgFeatureEntitlements.class);
  private final ResolvedOrganization first = ResolvedOrganization.fromKey("first");
  private final ResolvedOrganization second = ResolvedOrganization.fromKey("second");

  @ParameterizedTest
  @CsvSource({"true,2025.1,true", "false,2026.5,false", "false,2026.6,true", "false,26.6,false"})
  void it_should_preserve_architecture_deployment_and_version_gates(boolean cloud, String version, boolean supported) {
    when(configuration.isSonarQubeCloud()).thenReturn(cloud);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.ARCHITECTURE));

    var facts = ToolAvailability.resolve(configuration, api, Version.create(version), null, List.of());

    assertThat(facts.architecture()).isEqualTo(supported);
    verifyNoInteractions(api);
  }

  @Test
  void it_should_skip_remote_probes_for_disabled_toolsets() {
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.ISSUES));

    var facts = ToolAvailability.resolve(configuration, api, null, first, List.of(first));

    assertThat(facts.sara()).isFalse();
    assertThat(facts.dependencyRisks()).isFalse();
    assertThat(facts.vortexProxy()).isFalse();
    assertThat(facts.vortexAnalysis()).isFalse();
    verifyNoInteractions(api);
  }

  @Test
  void it_should_keep_http_entitlements_at_invocation_and_require_sara_opt_in() {
    when(configuration.isHttpEnabled()).thenReturn(true);
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.DEPENDENCY_RISKS, ToolCategory.VORTEX));

    assertThat(ToolAvailability.resolve(configuration, api, null, null, List.of()).sara()).isFalse();
    when(configuration.getEnabledToolsets()).thenReturn(ToolCategory.all());
    var facts = ToolAvailability.resolve(configuration, api, null, null, List.of());

    assertThat(facts.sara()).isTrue();
    assertThat(facts.dependencyRisks()).isTrue();
    assertThat(facts.vortexProxy()).isFalse();
    assertThat(facts.vortexAnalysis()).isFalse();
    verifyNoInteractions(api);
  }

  @Test
  void it_should_resolve_server_sca_once() {
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.DEPENDENCY_RISKS));
    var features = mock(FeaturesApi.class);
    when(api.featuresApi()).thenReturn(features);
    when(features.listFeatures()).thenReturn(Set.of(Feature.SCA));

    var facts = ToolAvailability.resolve(configuration, api, Version.create("2026.6"), null, List.of());

    assertThat(facts.dependencyRisks()).isTrue();
    verify(features).listFeatures();
  }

  @Test
  void it_should_check_only_the_selected_sara_organization_and_fail_closed() {
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.AGENTIC_READINESS));
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isSaraEnabledForOrg(first)).thenThrow(new IllegalStateException("unavailable"));

    assertThat(ToolAvailability.resolve(configuration, api, null, first, List.of(first, second)).sara()).isFalse();
    verify(entitlements, never()).isSaraEnabledForOrg(second);
  }

  @Test
  void it_should_try_other_organizations_for_sara_after_a_failed_check() {
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.AGENTIC_READINESS));
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isSaraEnabledForOrg(first)).thenThrow(new IllegalStateException("unavailable"));
    when(entitlements.isSaraEnabledForOrg(second)).thenReturn(true);

    assertThat(ToolAvailability.resolve(configuration, api, null, null, List.of(first, second)).sara()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"true,true,false,true,false", "true,false,false,true,false", "true,true,true,true,true", "false,false,false,true,true"})
  void it_should_preserve_vortex_proxy_and_analysis_independence(boolean cloud, boolean workspace, boolean allowAdvanced,
    boolean expectedProxy, boolean expectedAnalysis) {
    when(configuration.isSonarQubeCloud()).thenReturn(cloud);
    when(configuration.isForceLocalAnalysis()).thenReturn(!allowAdvanced);
    when(configuration.getWorkspacePath()).thenReturn(workspace ? Path.of("workspace") : null);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.ANALYSIS));
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isVortexEnabledForOrg(first)).thenReturn(true);

    var facts = ToolAvailability.resolve(configuration, api, null, first, List.of(first));

    assertThat(facts.vortexProxy()).isEqualTo(expectedProxy);
    assertThat(facts.vortexAnalysis()).isEqualTo(expectedAnalysis);
    verify(entitlements).isVortexEnabledForOrg(first);
  }
}
