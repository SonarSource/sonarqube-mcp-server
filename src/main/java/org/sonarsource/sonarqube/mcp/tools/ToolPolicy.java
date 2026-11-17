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

public record ToolPolicy(Set<ToolCategory> categories, Set<TransportMode> transports, Predicate<ToolCapabilities> requirement) {

  public ToolPolicy {
    categories = Set.copyOf(categories);
    transports = Set.copyOf(transports);
    Objects.requireNonNull(requirement);
  }

  public static ToolPolicy of(ToolCategory... categories) {
    return new ToolPolicy(Set.of(categories), Set.of(TransportMode.STDIO, TransportMode.HTTP), capabilities -> true);
  }

  public ToolPolicy stdioOnly() {
    return new ToolPolicy(categories, Set.of(TransportMode.STDIO), requirement);
  }

  public ToolPolicy requires(Predicate<ToolCapabilities> condition) {
    return new ToolPolicy(categories, transports, requirement.and(condition));
  }

  public boolean isSupported(TransportMode transport, ToolCapabilities capabilities) {
    return transports.contains(transport) && requirement.test(capabilities);
  }
}
