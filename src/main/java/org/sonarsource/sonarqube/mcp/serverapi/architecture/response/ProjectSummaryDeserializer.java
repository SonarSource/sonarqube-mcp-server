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
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import jakarta.annotation.Nullable;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.Coverage;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.DeviationCounts;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.FlawCounts;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectSummary.SmellCounts;

import static org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi.LANGUAGES;
import static org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureNumbers.MAX_SAFE_INTEGER;

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
      return new ProjectSummary(language.getAsString(), null, null, null, null);
    }
    SummaryData value = context.deserialize(json, SummaryData.class);
    var coverage = validateCoverage(value.coverage(), json.getAsJsonObject().get("coverage"));
    return new ProjectSummary(value.language(), value.deviations(), value.flaws(), value.smells(), coverage);
  }

  @Nullable
  private static Coverage validateCoverage(@Nullable Coverage coverage, @Nullable JsonElement raw) {
    if (coverage == null) {
      return null;
    }
    if (raw == null || !raw.isJsonObject()) {
      throw new JsonParseException("Invalid architecture coverage.");
    }
    var object = raw.getAsJsonObject();
    var score = checkedNumber(object, "coverage");
    var covered = checkedCount(object, "coveredArtifacts");
    var total = checkedCount(object, "totalArtifacts");
    if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0)) {
      throw new JsonParseException("Invalid architecture coverage.");
    }
    if (covered != null && total != null && covered.compareTo(total) > 0) {
      throw new JsonParseException("Invalid architecture coverage counts.");
    }
    return score == null && covered == null && total == null ? null : coverage;
  }

  @Nullable
  private static BigDecimal checkedNumber(JsonObject object, String key) {
    var value = object.get(key);
    if (value == null || value.isJsonNull()) {
      return null;
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new JsonParseException("Invalid architecture coverage.");
    }
    try {
      return value.getAsBigDecimal();
    } catch (NumberFormatException e) {
      throw new JsonParseException("Invalid architecture coverage.", e);
    }
  }

  @Nullable
  private static BigDecimal checkedCount(JsonObject object, String key) {
    var number = checkedNumber(object, key);
    if (number == null) {
      return null;
    }
    try {
      if (number.longValueExact() < 0 || number.compareTo(BigDecimal.valueOf(MAX_SAFE_INTEGER)) > 0) {
        throw new JsonParseException("Invalid architecture coverage counts.");
      }
      return number;
    } catch (ArithmeticException e) {
      throw new JsonParseException("Invalid architecture coverage counts.", e);
    }
  }

  private record SummaryData(String language, DeviationCounts deviations, FlawCounts flaws, SmellCounts smells,
                             ProjectSummary.Coverage coverage) {
  }
}
