/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.serverapi.system;

import java.util.Arrays;

public class Version {

  private final String name;
  private final int[] numbers;

  private Version(String version) {
    this.name = version.trim();
    var qualifierPosition = name.indexOf("-");
    String nameWithoutQualifier;
    if (qualifierPosition != -1) {
      nameWithoutQualifier = name.substring(0, qualifierPosition);
    } else {
      nameWithoutQualifier = this.name;
    }
    var split = nameWithoutQualifier.split("\\.");
    numbers = new int[split.length];
    for (var i = 0; i < split.length; i++) {
      numbers[i] = Integer.parseInt(split[i]);
    }
  }

  public boolean satisfiesMinRequirement(Version minRequirement) {
    var maxNumbers = Math.max(numbers.length, minRequirement.numbers.length);
    var myNumbers = Arrays.copyOf(numbers, maxNumbers);
    var otherNumbers = Arrays.copyOf(minRequirement.numbers, maxNumbers);
    for (var i = 0; i < maxNumbers; i++) {
      var compare = Integer.compare(myNumbers[i], otherNumbers[i]);
      if (compare != 0) {
        return compare >= 0;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return name;
  }

  public static Version create(String version) {
    return new Version(version);
  }
}
