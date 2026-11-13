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

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;

/** Reads summary counts without Gson's numeric coercion. */
public final class SummaryCountAdapter extends TypeAdapter<Long> {
  @Override
  public Long read(JsonReader reader) throws IOException {
    if (reader.peek() != JsonToken.NUMBER) {
      throw new JsonParseException("Invalid project summary count.");
    }
    try {
      long count = new BigDecimal(reader.nextString()).longValueExact();
      if (count < 0 || count > 9_007_199_254_740_991L) {
        throw new JsonParseException("Invalid project summary count.");
      }
      return count;
    } catch (NumberFormatException | ArithmeticException invalid) {
      throw new JsonParseException("Invalid project summary count.", invalid);
    }
  }

  @Override
  public void write(JsonWriter writer, Long value) throws IOException {
    writer.value(value);
  }
}
