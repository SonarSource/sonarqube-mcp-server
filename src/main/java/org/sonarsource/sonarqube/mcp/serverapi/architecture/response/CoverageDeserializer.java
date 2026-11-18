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

import static org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureNumbers.MAX_SAFE_INTEGER;

public final class CoverageDeserializer implements JsonDeserializer<Coverage> {
  @Override
  public Coverage deserialize(JsonElement value, Type type, JsonDeserializationContext context) throws JsonParseException {
    if (!value.isJsonObject()) throw new JsonParseException("Invalid architecture coverage.");
    var object = value.getAsJsonObject();
    var percentage = number(object, "coverage");
    var covered = count(object, "coveredArtifacts");
    var total = count(object, "totalArtifacts");
    if (percentage != null && (percentage.doubleValue() < 0 || percentage.doubleValue() > 100)) throw new JsonParseException("Invalid architecture coverage.");
    if (covered != null && total != null && covered > total) throw new JsonParseException("Invalid architecture coverage counts.");
    return new Coverage(percentage == null ? null : percentage.doubleValue(), covered, total);
  }

  @Nullable
  private static BigDecimal number(JsonObject object, String key) {
    var value = object.get(key);
    if (value == null || value.isJsonNull()) return null;
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new JsonParseException("Invalid architecture coverage.");
    try { return new BigDecimal(value.getAsString()); }
    catch (NumberFormatException e) { throw new JsonParseException("Invalid architecture coverage.", e); }
  }

  @Nullable
  private static Long count(JsonObject object, String key) {
    var value = number(object, key);
    if (value == null) return null;
    try {
      long result = value.longValueExact();
      if (result < 0 || result > MAX_SAFE_INTEGER) throw new JsonParseException("Invalid architecture coverage counts.");
      return result;
    } catch (ArithmeticException e) { throw new JsonParseException("Invalid architecture coverage counts.", e); }
  }
}
