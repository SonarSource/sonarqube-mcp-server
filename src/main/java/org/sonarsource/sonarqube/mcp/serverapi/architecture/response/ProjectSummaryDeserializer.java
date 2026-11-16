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
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.DeviationCounts;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.FlawCounts;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.SmellCounts;

import static org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi.LANGUAGES;

/** Skip unsupported languages before decoding their evolving payloads. */
public final class ProjectSummaryDeserializer implements JsonDeserializer<ProjectSummary> {
  @Override
  public ProjectSummary deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Invalid project summary.");
    }
    var language = json.getAsJsonObject().get("language");
    if (language == null || !language.isJsonPrimitive() || !language.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("Invalid project summary language.");
    }
    if (!LANGUAGES.contains(language.getAsString())) {
      return new ProjectSummary(language.getAsString(), null, null, null);
    }
    SummaryData value = context.deserialize(json, SummaryData.class);
    return new ProjectSummary(value.language(), value.deviations(), value.flaws(), value.smells());
  }

  private record SummaryData(String language, DeviationCounts deviations, FlawCounts flaws, SmellCounts smells) {
  }
}
