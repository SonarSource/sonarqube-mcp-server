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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.Deployment;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.StartupRequirement;

/** Selects tools once at startup, before either MCP transport is initialized. */
public final class ToolRegistrar {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final Set<ToolCategory> enabledCategories;
  private final boolean readOnly;
  private final TransportMode transport;
  private final Deployment deployment;
  private final Predicate<StartupRequirement> requirementResolver;
  private final EnumMap<StartupRequirement, Boolean> requirements = new EnumMap<>(StartupRequirement.class);
  private final List<Tool> tools = new ArrayList<>();
  private final Set<String> instructions = new LinkedHashSet<>();

  public ToolRegistrar(Set<ToolCategory> enabledCategories, boolean readOnly, TransportMode transport, Deployment deployment,
    Predicate<StartupRequirement> requirementResolver) {
    this.enabledCategories = Set.copyOf(enabledCategories);
    this.readOnly = readOnly;
    this.transport = transport;
    this.deployment = deployment;
    this.requirementResolver = requirementResolver;
  }

  public void register(Tool tool) {
    if (isAllowed(tool)) {
      tools.add(tool);
    }
  }

  public void registerAll(List<? extends Tool> candidates) {
    registerAll(candidates, null);
  }

  public void registerAll(List<? extends Tool> candidates, @Nullable String groupInstructions) {
    var previousSize = tools.size();
    candidates.forEach(this::register);
    if (tools.size() > previousSize && groupInstructions != null && !groupInstructions.isBlank()) {
      instructions.add(groupInstructions);
    }
  }

  public List<Tool> registeredTools() {
    return List.copyOf(tools);
  }

  public List<String> instructions() {
    return List.copyOf(instructions);
  }

  private boolean isAllowed(Tool tool) {
    if (!tool.getCategories().contains(ToolCategory.PROJECTS) && tool.getCategories().stream().noneMatch(enabledCategories::contains)) {
      return false;
    }
    var annotations = tool.definition().annotations();
    if (readOnly && (annotations == null || !Boolean.TRUE.equals(annotations.readOnlyHint()))) {
      return false;
    }
    var policy = tool.availabilityPolicy();
    return policy.transports().contains(transport) && policy.deployments().contains(deployment)
      && policy.requirementsFor(transport).stream().allMatch(requirement -> requirements.computeIfAbsent(requirement, this::resolveRequirement));
  }

  private boolean resolveRequirement(StartupRequirement requirement) {
    try {
      return requirementResolver.test(requirement);
    } catch (Exception e) {
      LOG.warn("Could not check tool startup requirement '" + requirement + "': " + e.getMessage());
      return false;
    }
  }
}
