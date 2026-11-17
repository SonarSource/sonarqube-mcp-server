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

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

/** Startup transport and capability requirements; configuration and request filtering apply separately. */
public record ToolAvailability(Set<ToolCategory> categories, Set<TransportMode> transports, Predicate<ToolCapabilities> requirement) {

  public ToolAvailability {
    categories = Set.copyOf(categories);
    transports = Set.copyOf(transports);
    Objects.requireNonNull(requirement);
  }

  public static ToolAvailability inCategory(ToolCategory category) {
    return inCategories(category);
  }

  public static ToolAvailability inCategories(ToolCategory... categories) {
    return new ToolAvailability(Set.of(categories), Set.of(TransportMode.STDIO, TransportMode.HTTP), capabilities -> true);
  }

  public ToolAvailability stdioOnly() {
    return new ToolAvailability(categories, Set.of(TransportMode.STDIO), requirement);
  }

  public ToolAvailability cloudOnly() {
    return requires(ToolCapabilities::cloud);
  }

  public ToolAvailability serverOnly() {
    return requires(ToolCapabilities::server);
  }

  public ToolAvailability requiresIdeAnalysis() {
    return requires(ToolCapabilities::ideAnalysis);
  }

  public ToolAvailability requires(Predicate<ToolCapabilities> condition) {
    return new ToolAvailability(categories, transports, requirement.and(condition));
  }

  public boolean isAvailable(TransportMode transport, ToolCapabilities capabilities) {
    return transports.contains(transport) && requirement.test(capabilities);
  }
}
