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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.StreamSupport;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

public record ProjectFinding(String type, String category, String language, Details details, List<String> files) {
  public sealed interface Details permits StructuralDetails, RelationshipDetails, TangleDetails, ComponentDetails {
  }

  public record StructuralDetails(String reason, String nodeKey, @Nullable Integer misplacedComponentCount) implements Details {
  }

  public record RelationshipDetails(String reason, String fromNodeKey, String toNodeKey, @Nullable Integer dependencyCount) implements Details {
  }

  public record TangleDetails(List<String> nodeKeys, int nodeCount) implements Details {
  }

  public record ComponentDetails(String nodeKey, @Nullable Integer leafArtifactCount, @Nullable Integer fragmentCount) implements Details {
  }

  @Nullable
  public static ProjectFinding parse(JsonElement value) {
    var row = objectValue(value);
    var type = stringValue(row.get("type"));
    if (type.isBlank()) {
      throw new IllegalStateException("Invalid public finding type.");
    }
    if (!ArchitectureApi.FINDING_TYPES.contains(type)) {
      return null;
    }
    var category = stringValue(row.get("category"));
    if (!categoryOf(type).equals(category)) {
      throw new IllegalStateException("Invalid public finding category.");
    }
    var language = stringValue(row.get("language"));
    if (language.isBlank()) {
      throw new IllegalStateException("Invalid public finding language.");
    }
    if (!ArchitectureApi.LANGUAGES.contains(language)) {
      return null;
    }
    var details = objectValue(row.get("details"));
    if (type.equals("structuralDeviation") || type.equals("relationshipDeviation")) {
      var reason = stringValue(details.get("reason"));
      if (!reason.isBlank() && !List.of("misplacedComponent", "unimplementedComponent", "disallowedDependency", "interfaceViolation").contains(reason)) {
        return null;
      }
    }
    return new ProjectFinding(type, category, language, parseDetails(type, details), strings(row.get("files")));
  }

  private static Details parseDetails(String type, JsonObject details) {
    return switch (type) {
      case "structuralDeviation" -> new StructuralDetails(reason(details, List.of("misplacedComponent", "unimplementedComponent")),
        stringValue(details.get("nodeKey")), optionalCount(details, "misplacedComponentCount"));
      case "relationshipDeviation" -> new RelationshipDetails(reason(details, List.of("disallowedDependency", "interfaceViolation")),
        stringValue(details.get("fromNodeKey")), stringValue(details.get("toNodeKey")), optionalCount(details, "dependencyCount"));
      case "tangle", "weakTangle" -> new TangleDetails(strings(details.get("nodeKeys")), count(details.get("nodeCount")));
      default -> new ComponentDetails(stringValue(details.get("nodeKey")), optionalCount(details, "leafArtifactCount"), optionalCount(details, "fragmentCount"));
    };
  }

  public static String categoryOf(String type) {
    return switch (type) {
      case "structuralDeviation", "relationshipDeviation" -> "deviation";
      case "tangle", "oversizedComponent" -> "flaw";
      default -> "smell";
    };
  }

  private static JsonObject objectValue(@Nullable JsonElement value) {
    if (value == null || !value.isJsonObject()) {
      throw new IllegalStateException("Invalid public finding object.");
    }
    return value.getAsJsonObject();
  }

  private static String stringValue(@Nullable JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalStateException("Invalid public finding string.");
    }
    return value.getAsString();
  }

  private static List<String> strings(@Nullable JsonElement value) {
    if (value == null || !value.isJsonArray()) {
      throw new IllegalStateException("Invalid public finding string array.");
    }
    return StreamSupport.stream(value.getAsJsonArray().spliterator(), false).map(ProjectFinding::stringValue).toList();
  }

  private static String reason(JsonObject details, List<String> reasons) {
    var reason = stringValue(details.get("reason"));
    if (!reasons.contains(reason)) {
      throw new IllegalStateException("Invalid public finding reason.");
    }
    return reason;
  }

  @Nullable
  private static Integer optionalCount(JsonObject details, String name) {
    var value = details.get(name);
    return value == null || value.isJsonNull() ? null : count(value);
  }

  private static int count(@Nullable JsonElement value) {
    try {
      if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || value.getAsBigDecimal().signum() < 0) {
        throw new IllegalStateException("Invalid public finding count.");
      }
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalStateException("Invalid public finding count.", e);
    }
  }
}
