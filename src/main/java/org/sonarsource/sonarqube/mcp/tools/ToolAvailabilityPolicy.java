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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

/** Startup availability. Request-specific eligibility remains in {@link Tool#isEnabledFor}. */
public record ToolAvailabilityPolicy(Set<TransportMode> transports, Set<Deployment> deployments,
  Map<TransportMode, Set<StartupRequirement>> startupRequirements) {

  public enum Deployment {
    CLOUD, SERVER
  }

  public enum StartupRequirement {
    SARA_ENABLED
  }

  public static final ToolAvailabilityPolicy UNRESTRICTED = new ToolAvailabilityPolicy(Set.of(TransportMode.values()), Set.of(Deployment.values()), Map.of());
  public static final ToolAvailabilityPolicy CLOUD_ONLY = new ToolAvailabilityPolicy(Set.of(TransportMode.values()), Set.of(Deployment.CLOUD), Map.of());
  public static final ToolAvailabilityPolicy SERVER_ONLY = new ToolAvailabilityPolicy(Set.of(TransportMode.values()), Set.of(Deployment.SERVER), Map.of());

  public ToolAvailabilityPolicy {
    transports = Set.copyOf(transports);
    deployments = Set.copyOf(deployments);
    startupRequirements = startupRequirements.entrySet().stream()
      .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }

  public Set<StartupRequirement> requirementsFor(TransportMode transport) {
    return startupRequirements.getOrDefault(transport, Set.of());
  }
}
