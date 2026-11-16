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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.OrgFeatureEntitlements;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.system.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.LOCAL;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.NONE;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.VORTEX;

class ToolCapabilitiesResolverTest {

  private static final ResolvedOrganization ORGANIZATION = ResolvedOrganization.fromKey("org");
  private final ServerApi api = mock(ServerApi.class);
  private final HttpClientProvider http = mock(HttpClientProvider.class);
  @TempDir
  Path storage;

  @ParameterizedTest
  @CsvSource({"2026.5, false", "2026.6, true", "26.6, false"})
  void it_should_apply_the_architecture_version_gate_without_feature_probes(String version, boolean supported) {
    var resolver = new ToolCapabilitiesResolver(configuration(Map.of("SONARQUBE_TOOLSETS", "issues")), api, http, Version.create(version));

    var capabilities = resolver.resolve(null, List.of());

    assertThat(capabilities.architecture()).isEqualTo(supported);
    assertThat(capabilities.analysisMode()).isEqualTo(LOCAL);
    verifyNoInteractions(api, http);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_resolve_http_capabilities_without_startup_entitlement_probes(boolean startupToken) {
    var environment = new HashMap<>(Map.of("SONARQUBE_TRANSPORT", "http", "SONARQUBE_IS_CLOUD", "true",
      "SONARQUBE_TOOLSETS", "analysis,dependency-risks,agentic-readiness"));
    if (startupToken) {
      environment.put("SONARQUBE_TOKEN", "startup-token");
    }
    var resolver = new ToolCapabilitiesResolver(configuration(environment), api, http, null);

    var capabilities = resolver.resolve(null, List.of());

    assertThat(capabilities.architecture()).isTrue();
    assertThat(capabilities.dependencyRisks()).isTrue();
    assertThat(capabilities.agenticReadiness()).isTrue();
    assertThat(capabilities.vortexProxy()).isFalse();
    assertThat(capabilities.analysisMode()).isEqualTo(startupToken ? LOCAL : NONE);
    verifyNoInteractions(api, http);
  }

  @Test
  void it_should_keep_http_agentic_readiness_opt_in() {
    var resolver = new ToolCapabilitiesResolver(configuration(Map.of("SONARQUBE_TRANSPORT", "http", "SONARQUBE_IS_CLOUD", "true")), api, http, null);

    assertThat(resolver.resolve(null, List.of()).agenticReadiness()).isFalse();
    verifyNoInteractions(api, http);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_probe_server_dependency_risks_once(boolean enabled) {
    var features = mock(FeaturesApi.class);
    when(api.featuresApi()).thenReturn(features);
    when(features.listFeatures()).thenReturn(enabled ? Set.of(Feature.SCA) : Set.of());
    var resolver = new ToolCapabilitiesResolver(configuration(Map.of("SONARQUBE_TOOLSETS", "dependency-risks")), api, http, Version.create("2026.6"));

    assertThat(resolver.resolve(null, List.of()).dependencyRisks()).isEqualTo(enabled);
    verify(features).listFeatures();
    verifyNoMoreInteractions(features);
  }

  @Test
  void it_should_share_the_vortex_decision_between_analysis_and_proxies(@TempDir Path workspace) {
    var entitlements = mock(OrgFeatureEntitlements.class);
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isVortexEnabledForOrg(ORGANIZATION)).thenReturn(true);
    var configuration = mock(McpServerLaunchConfiguration.class);
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.isToolCategoryEnabled(ToolCategory.VORTEX)).thenReturn(true);
    when(configuration.getWorkspacePath()).thenReturn(workspace);
    var resolver = new ToolCapabilitiesResolver(configuration, api, http, null);

    var capabilities = resolver.resolve(ORGANIZATION, List.of(ORGANIZATION));

    assertThat(capabilities.vortexProxy()).isTrue();
    assertThat(capabilities.analysisMode()).isEqualTo(VORTEX);
    verify(entitlements, times(1)).isVortexEnabledForOrg(ORGANIZATION);
    verifyNoMoreInteractions(entitlements);
  }

  @Test
  void it_should_keep_server_analysis_unavailable_when_entitled_without_a_workspace() {
    var entitlements = mock(OrgFeatureEntitlements.class);
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isVortexEnabledForOrg(null)).thenReturn(true);
    var resolver = new ToolCapabilitiesResolver(configuration(Map.of("SONARQUBE_TOOLSETS", "vortex")), api, http, Version.create("2026.6"));

    var capabilities = resolver.resolve(null, List.of());

    assertThat(capabilities.vortexProxy()).isTrue();
    assertThat(capabilities.analysisMode()).isEqualTo(NONE);
    verify(entitlements).isVortexEnabledForOrg(null);
    verifyNoMoreInteractions(entitlements);
  }

  @Test
  void it_should_continue_agentic_readiness_checks_after_a_failed_organization() {
    var second = ResolvedOrganization.fromKey("second");
    var entitlements = mock(OrgFeatureEntitlements.class);
    when(api.orgFeatureEntitlements()).thenReturn(entitlements);
    when(entitlements.isSaraEnabledForOrg(ORGANIZATION)).thenThrow(new IllegalStateException("unavailable"));
    when(entitlements.isSaraEnabledForOrg(second)).thenReturn(true);
    var resolver = new ToolCapabilitiesResolver(configuration(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "agentic-readiness")), api, http, null);

    assertThat(resolver.resolve(null, List.of(ORGANIZATION, second)).agenticReadiness()).isTrue();
    verify(entitlements).isSaraEnabledForOrg(ORGANIZATION);
    verify(entitlements).isSaraEnabledForOrg(second);
    verifyNoMoreInteractions(entitlements);
  }

  private McpServerLaunchConfiguration configuration(Map<String, String> overrides) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_URL", "http://localhost:9000");
    if (!"http".equals(overrides.get("SONARQUBE_TRANSPORT"))) {
      environment.put("SONARQUBE_TOKEN", "test-token");
    }
    environment.putAll(overrides);
    return new McpServerLaunchConfiguration(environment);
  }
}
