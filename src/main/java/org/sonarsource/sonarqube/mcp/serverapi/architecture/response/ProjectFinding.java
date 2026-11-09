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
import java.util.Objects;

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
    public Finding {
      Objects.requireNonNull(type);
      Objects.requireNonNull(language);
      Objects.requireNonNull(details);
      files = List.copyOf(files);
      if (!categoryOf(type).equals(category)) {
        throw new IllegalStateException("Invalid public finding category.");
      }
    }
  }

  sealed interface Details {
  }

  record StructuralDetails(String reason, String nodeKey, @Nullable BigDecimal misplacedComponentCount) implements Details {
    public StructuralDetails {
      Objects.requireNonNull(nodeKey);
      requireReason(reason, List.of("misplacedComponent", "unimplementedComponent"));
      misplacedComponentCount = integerCount(misplacedComponentCount);
    }
  }

  record RelationshipDetails(String reason, String fromNodeKey, String toNodeKey, @Nullable BigDecimal dependencyCount) implements Details {
    public RelationshipDetails {
      Objects.requireNonNull(fromNodeKey);
      Objects.requireNonNull(toNodeKey);
      requireReason(reason, List.of("disallowedDependency", "interfaceViolation"));
      dependencyCount = integerCount(dependencyCount);
    }
  }

  record TangleDetails(List<String> nodeKeys, BigDecimal nodeCount) implements Details {
    public TangleDetails {
      nodeKeys = List.copyOf(nodeKeys);
      Objects.requireNonNull(nodeCount);
      nodeCount = integerCount(nodeCount);
    }
  }

  record ComponentDetails(String nodeKey, @Nullable BigDecimal leafArtifactCount, @Nullable BigDecimal fragmentCount) implements Details {
    public ComponentDetails {
      Objects.requireNonNull(nodeKey);
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

  private static void requireReason(String reason, List<String> reasons) {
    if (!reasons.contains(Objects.requireNonNull(reason))) {
      throw new IllegalStateException("Invalid public finding reason for type.");
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
