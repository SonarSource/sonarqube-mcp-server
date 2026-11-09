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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Nullable;
import java.lang.reflect.Type;
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

  /** Selects the documented details variant; Gson decodes the complete finding DTO once. */
  final class Deserializer implements JsonDeserializer<ProjectFinding> {
    private static final List<String> LANGUAGES = List.of("java", "cs", "js", "ts", "py");
    private static final List<String> REASONS = List.of("misplacedComponent", "unimplementedComponent", "disallowedDependency", "interfaceViolation");

    @Override
    public ProjectFinding deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
      var object = json.getAsJsonObject();
      var type = required(context.deserialize(object.get("type"), String.class));
      var detailsType = switch (type) {
        case "structuralDeviation" -> StructuralDetails.class;
        case "relationshipDeviation" -> RelationshipDetails.class;
        case "tangle", "weakTangle" -> TangleDetails.class;
        case "oversizedComponent", "splitResponsibility" -> ComponentDetails.class;
        default -> null;
      };
      // Preserve unsupported metadata for filter checks and skipped-row counts without decoding an unknown payload.
      if (detailsType == null) {
        return context.deserialize(json, UnsupportedFinding.class);
      }
      var language = required(context.deserialize(object.get("language"), String.class));
      if (!LANGUAGES.contains(language)) {
        return context.deserialize(json, UnsupportedFinding.class);
      }
      if (detailsType == StructuralDetails.class || detailsType == RelationshipDetails.class) {
        var reason = required(context.deserialize(object.getAsJsonObject("details").get("reason"), String.class));
        if (!REASONS.contains(reason)) {
          return context.deserialize(json, UnsupportedFinding.class);
        }
      }
      return context.deserialize(json, TypeToken.getParameterized(Finding.class, detailsType).getType());
    }

    private static String required(String value) {
      if (Objects.requireNonNull(value).isBlank()) {
        throw new IllegalStateException("Missing public finding discriminator.");
      }
      return value;
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
