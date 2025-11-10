/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.serverapi.features;

import java.util.Arrays;
import java.util.Optional;

public enum Feature {
  SCA("sca");

  public static Optional<Feature> fromKey(String key) {
    return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
  }

  private final String key;

  Feature(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
