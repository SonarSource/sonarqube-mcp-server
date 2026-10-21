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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.List;

/**
 * {@code mode} is {@code dry-run}, {@code unchanged}, {@code created} or {@code replaced}.
 */
public record ArchitectureSetToolResponse(String projectKey, String mode, Diff diff, List<String> warnings) {

  public record Diff(List<String> removed, List<String> added, List<String> changed) {
    boolean isEmpty() {
      return removed.isEmpty() && added.isEmpty() && changed.isEmpty();
    }
  }

}
