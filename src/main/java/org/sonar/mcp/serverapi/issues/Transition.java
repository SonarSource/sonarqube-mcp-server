/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.mcp.serverapi.issues;

import java.util.Arrays;
import java.util.Optional;

public enum Transition {

  ACCEPT("accept"),
  FALSE_POSITIVE("falsepositive"),
  REOPEN("reopen");

  private final String status;

  Transition(String status) {
    this.status = status;
  }

  public static Optional<Transition> fromStatus(String status) {
    return Arrays.stream(values()).filter(transition -> transition.status.equals(status)).findFirst();
  }

  public String getStatus() {
    return status;
  }

}
