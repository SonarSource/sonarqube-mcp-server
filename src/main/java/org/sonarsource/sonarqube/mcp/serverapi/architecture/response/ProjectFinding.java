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
package org.sonarsource.sonarqube.mcp.serverapi.architecture.response;

import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

public sealed interface ProjectFinding {
  String type();

  @Nullable
  String category();

  @Nullable
  String language();

  default boolean supported() {
    return true;
  }

  record Finding<D extends Details>(String type, String category, String language, D details, List<String> files) implements ProjectFinding {
  }

  sealed interface Details {
  }

  record StructuralDetails(String reason, String nodeKey, @Nullable BigDecimal misplacedComponentCount) implements Details {
    public StructuralDetails {
      misplacedComponentCount = integerCount(misplacedComponentCount);
    }
  }

  record RelationshipDetails(String reason, String fromNodeKey, String toNodeKey, @Nullable BigDecimal dependencyCount) implements Details {
    public RelationshipDetails {
      dependencyCount = integerCount(dependencyCount);
    }
  }

  record TangleDetails(List<String> nodeKeys, BigDecimal nodeCount) implements Details {
    public TangleDetails {
      nodeCount = integerCount(nodeCount);
    }
  }

  record ComponentDetails(String nodeKey, @Nullable BigDecimal leafArtifactCount, @Nullable BigDecimal fragmentCount) implements Details {
    public ComponentDetails {
      leafArtifactCount = integerCount(leafArtifactCount);
      fragmentCount = integerCount(fragmentCount);
    }
  }

  record UnsupportedFinding(String type, @Nullable String category, @Nullable String language) implements ProjectFinding {
    @Override
    public boolean supported() {
      return false;
    }
  }

  @Nullable
  private static BigDecimal integerCount(@Nullable BigDecimal count) {
    if (count == null) {
      return null;
    }
    var value = count.intValueExact();
    if (value < 0) {
      throw new IllegalStateException("Finding counts must be nonnegative.");
    }
    return BigDecimal.valueOf(value);
  }

  static String categoryOf(String type) {
    return switch (type) {
      case "structuralDeviation", "relationshipDeviation" -> "deviation";
      case "tangle", "oversizedComponent" -> "flaw";
      default -> "smell";
    };
  }
}
