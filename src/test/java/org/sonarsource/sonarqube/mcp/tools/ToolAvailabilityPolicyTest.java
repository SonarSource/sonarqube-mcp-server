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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.Deployment;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.StartupRequirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolAvailabilityPolicyTest {

  @Test
  void it_should_default_to_all_transports_and_deployments_without_requirements() {
    var policy = ToolAvailabilityPolicy.builder().build();

    assertThat(policy.transports()).containsExactlyInAnyOrder(TransportMode.STDIO, TransportMode.HTTP);
    assertThat(policy.deployments()).containsExactlyInAnyOrder(Deployment.CLOUD, Deployment.SERVER);
    assertThat(policy.startupRequirements()).isEmpty();
  }

  @Test
  void it_should_restrict_support_to_selected_transports_and_deployments() {
    var policy = ToolAvailabilityPolicy.builder()
      .transports(TransportMode.STDIO)
      .deployments(Deployment.CLOUD)
      .build();

    assertThat(policy.transports()).containsExactly(TransportMode.STDIO);
    assertThat(policy.deployments()).containsExactly(Deployment.CLOUD);
    assertThat(policy.startupRequirements()).isEmpty();
  }

  @Test
  void it_should_accumulate_requirements_per_transport_without_duplicates() {
    var policy = ToolAvailabilityPolicy.builder()
      .require(TransportMode.STDIO, StartupRequirement.SARA_ENABLED)
      .require(TransportMode.HTTP, StartupRequirement.SARA_ENABLED)
      .require(TransportMode.STDIO, StartupRequirement.SARA_ENABLED)
      .build();

    assertThat(policy.requirementsFor(TransportMode.STDIO)).containsExactly(StartupRequirement.SARA_ENABLED);
    assertThat(policy.requirementsFor(TransportMode.HTTP)).containsExactly(StartupRequirement.SARA_ENABLED);
  }

  @Test
  void it_should_snapshot_builder_state_on_each_build() {
    var builder = ToolAvailabilityPolicy.builder()
      .transports(TransportMode.STDIO)
      .deployments(Deployment.CLOUD)
      .require(TransportMode.STDIO, StartupRequirement.SARA_ENABLED);
    var first = builder.build();

    var second = builder.transports(TransportMode.HTTP)
      .deployments(Deployment.SERVER)
      .require(TransportMode.HTTP, StartupRequirement.SARA_ENABLED)
      .build();

    assertThat(first.transports()).containsExactly(TransportMode.STDIO);
    assertThat(first.deployments()).containsExactly(Deployment.CLOUD);
    assertThat(first.requirementsFor(TransportMode.STDIO)).containsExactly(StartupRequirement.SARA_ENABLED);
    assertThat(first.requirementsFor(TransportMode.HTTP)).isEmpty();
    assertThat(second.transports()).containsExactly(TransportMode.HTTP);
    assertThat(second.deployments()).containsExactly(Deployment.SERVER);
    assertThat(second.requirementsFor(TransportMode.HTTP)).containsExactly(StartupRequirement.SARA_ENABLED);
  }

  @Test
  void it_should_copy_builder_input_arrays() {
    var transports = new TransportMode[] {TransportMode.STDIO};
    var deployments = new Deployment[] {Deployment.CLOUD};
    var builder = ToolAvailabilityPolicy.builder().transports(transports).deployments(deployments);

    transports[0] = TransportMode.HTTP;
    deployments[0] = Deployment.SERVER;
    var policy = builder.build();

    assertThat(policy.transports()).containsExactly(TransportMode.STDIO);
    assertThat(policy.deployments()).containsExactly(Deployment.CLOUD);
  }

  @Test
  void it_should_defensively_copy_all_policy_collections() {
    var transports = EnumSet.of(TransportMode.STDIO);
    var deployments = EnumSet.of(Deployment.CLOUD);
    var requirements = EnumSet.of(StartupRequirement.SARA_ENABLED);
    var requirementsByTransport = new EnumMap<TransportMode, Set<StartupRequirement>>(TransportMode.class);
    requirementsByTransport.put(TransportMode.STDIO, requirements);
    var policy = new ToolAvailabilityPolicy(transports, deployments, requirementsByTransport);

    transports.clear();
    deployments.clear();
    requirements.clear();
    requirementsByTransport.clear();

    assertThat(policy.transports()).containsExactly(TransportMode.STDIO);
    assertThat(policy.deployments()).containsExactly(Deployment.CLOUD);
    assertThat(policy.requirementsFor(TransportMode.STDIO)).containsExactly(StartupRequirement.SARA_ENABLED);
    assertThat(policy.requirementsFor(TransportMode.HTTP)).isEmpty();
    assertThatThrownBy(() -> policy.transports().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> policy.deployments().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> policy.startupRequirements().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> policy.requirementsFor(TransportMode.STDIO).clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
