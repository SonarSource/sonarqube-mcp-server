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
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.ComponentDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.Finding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.RelationshipDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.StructuralDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.TangleDetails;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding.UnsupportedFinding;

/** Selects the documented details variant; Gson decodes the complete finding DTO once. */
public final class ProjectFindingDeserializer implements JsonDeserializer<ProjectFinding> {
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
