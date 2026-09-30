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
package org.sonarsource.sonarqube.mcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializes tool response records to JSON text.
 */
public class SchemaUtils {

  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
    .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
    .build();

  private SchemaUtils() {
    // Static class
  }

  public static String toJsonString(Record response) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to convert response to JSON string", e);
    }
  }

}
