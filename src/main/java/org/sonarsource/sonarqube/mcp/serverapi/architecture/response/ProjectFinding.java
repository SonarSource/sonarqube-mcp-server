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
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

public record ProjectFinding(String type, String category, String language, Details details, List<String> files) {
  private static final String STRUCTURAL_DEVIATION = "structuralDeviation";
  private static final String RELATIONSHIP_DEVIATION = "relationshipDeviation";
  private static final List<String> DEVIATION_REASONS = List.of(
    "misplacedComponent", "unimplementedComponent", "disallowedDependency", "interfaceViolation");

  public ProjectFinding {
    Objects.requireNonNull(details);
    files = List.copyOf(files);
  }

  public record Details(@Nullable String reason, @Nullable String nodeKey, @Nullable String fromNodeKey, @Nullable String toNodeKey,
    @Nullable List<String> nodeKeys, @Nullable Integer nodeCount, @Nullable Integer misplacedComponentCount, @Nullable Integer dependencyCount,
    @Nullable Integer leafArtifactCount, @Nullable Integer fragmentCount) {
  }

  public boolean supported() {
    if (type.isBlank() || language.isBlank()) {
      throw new IllegalStateException("Invalid public finding type or language.");
    }
    if (!ArchitectureApi.FINDING_TYPES.contains(type) || !ArchitectureApi.LANGUAGES.contains(language)) {
      return false;
    }
    if (!categoryOf(type).equals(category)) {
      throw new IllegalStateException("Invalid public finding category.");
    }
    if (Stream.of(details.nodeCount(), details.misplacedComponentCount(), details.dependencyCount(), details.leafArtifactCount(), details.fragmentCount())
      .filter(Objects::nonNull).anyMatch(count -> count < 0)) {
      throw new IllegalStateException("Finding counts must be nonnegative.");
    }
    if (!List.of(STRUCTURAL_DEVIATION, RELATIONSHIP_DEVIATION).contains(type)) {
      return true;
    }
    var reason = details.reason();
    if (reason.isBlank()) {
      throw new IllegalStateException("Invalid public finding reason.");
    }
    if (!DEVIATION_REASONS.contains(reason)) {
      return false;
    }
    var reasons = type.equals(STRUCTURAL_DEVIATION) ? List.of("misplacedComponent", "unimplementedComponent")
      : List.of("disallowedDependency", "interfaceViolation");
    if (!reasons.contains(reason)) {
      throw new IllegalStateException("Invalid public finding reason for type.");
    }
    return true;
  }

  public static String categoryOf(String type) {
    return switch (type) {
      case STRUCTURAL_DEVIATION, RELATIONSHIP_DEVIATION -> "deviation";
      case "tangle", "oversizedComponent" -> "flaw";
      default -> "smell";
    };
  }
}
