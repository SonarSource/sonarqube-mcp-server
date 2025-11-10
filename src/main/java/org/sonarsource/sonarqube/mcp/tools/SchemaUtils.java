/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Utility class for generating JSON schemas from Java classes and serializing objects.
 * Uses jsonschema-generator library for schema generation.
 */
public class SchemaUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  
  private static final SchemaGenerator SCHEMA_GENERATOR;

  static {
    var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
      .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED));
    configBuilder.forFields()
      .withRequiredCheck(field -> field.getAnnotationConsideringFieldAndGetter(Nullable.class) == null);
    configBuilder.forMethods()
      .withRequiredCheck(method -> method.getAnnotationConsideringFieldAndGetter(Nullable.class) == null);
    SCHEMA_GENERATOR = new SchemaGenerator(configBuilder.build());
  }

  private SchemaUtils() {
    // Static class
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> generateOutputSchema(Class<? extends Record> clazz) {
    var schemaNode = SCHEMA_GENERATOR.generateSchema(clazz);
    var schema = OBJECT_MAPPER.convertValue(schemaNode, Map.class);
    schema.remove("$schema");
    return schema;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toStructuredContent(Record obj) {
    return OBJECT_MAPPER.convertValue(obj, Map.class);
  }

  public static String toJsonString(Record response) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to convert response to JSON string", e);
    }
  }

}

