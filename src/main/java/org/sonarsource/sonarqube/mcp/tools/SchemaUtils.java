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
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for generating JSON schemas from Java classes and serializing objects.
 * Supports both records and regular classes, with automatic schema generation from structure.
 */
public class SchemaUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final String STRING = "string";
  private static final String BOOLEAN = "boolean";
  private static final String NUMBER = "number";
  private static final String OBJECT = "object";
  private static final String ITEMS = "items";

  private SchemaUtils() {
    // Static class
  }

  /**
   * Generate a JSON schema from a Java class using reflection.
   * This creates an output schema suitable for MCP tools.
   * Supports records (preferred) and regular classes with fields.
   */
  public static Map<String, Object> generateOutputSchema(Class<?> clazz) {
    if (clazz.isRecord()) {
      return generateSchemaFromRecord(clazz);
    }
    return generateSchemaFromFields(clazz);
  }

  /**
   * Generate schema from a Java record (preferred approach).
   * Reads record components and their annotations.
   */
  private static Map<String, Object> generateSchemaFromRecord(Class<?> recordClass) {
    var properties = new HashMap<String, Object>();
    var required = new ArrayList<String>();

    for (var component : recordClass.getRecordComponents()) {
      var name = component.getName();
      
      var propertySchema = generatePropertySchemaFromComponent(component);
      properties.put(name, propertySchema);
      
      // Check if @Nullable - if not, it's required
      if (!isNullable(component)) {
        required.add(name);
      }
    }

    return Map.of(
      "type", OBJECT,
      "properties", properties,
      "required", required
    );
  }

  /**
   * Generate schema from regular class fields (fallback).
   */
  private static Map<String, Object> generateSchemaFromFields(Class<?> clazz) {
    var properties = new HashMap<String, Object>();
    var required = new ArrayList<String>();

    for (var field : clazz.getDeclaredFields()) {
      var fieldName = field.getName();
      var propertySchema = generatePropertySchemaFromField(field);
      properties.put(fieldName, propertySchema);

      if (!isNullable(field)) {
        required.add(fieldName);
      }
    }

    return Map.of(
      "type", OBJECT,
      "properties", properties,
      "required", required
    );
  }

  /**
   * Generate schema for a record component with type inference and description support.
   */
  private static Map<String, Object> generatePropertySchemaFromComponent(RecordComponent component) {
    var schema = new HashMap<String, Object>();
    Class<?> type = component.getType();

    if (type == String.class) {
      schema.put("type", STRING);
    } else if (type == int.class || type == Integer.class || 
               type == long.class || type == Long.class ||
               type == double.class || type == Double.class) {
      schema.put("type", NUMBER);
    } else if (type == boolean.class || type == Boolean.class) {
      schema.put("type", BOOLEAN);
    } else if (List.class.isAssignableFrom(type)) {
      schema.put("type", "array");
      
      // Infer item type from generic parameter
      var genericType = component.getGenericType();
      if (genericType instanceof ParameterizedType paramType) {
        var itemType = paramType.getActualTypeArguments()[0];
        if (itemType instanceof Class<?> itemClass) {
          schema.put(ITEMS, generateItemSchema(itemClass));
        }
      } else {
        schema.put(ITEMS, Map.of("type", OBJECT));
      }
    } else {
      // Nested object - recursively generate schema
      var nestedSchema = generateOutputSchema(type);
      schema.putAll(nestedSchema);
    }

    // Add description from @Description annotation or generate from field name
    var description = getDescriptionFromComponent(component);
    schema.put("description", description);

    return schema;
  }

  /**
   * Generate schema for a field (fallback when not using records).
   */
  private static Map<String, Object> generatePropertySchemaFromField(Field field) {
    var schema = new HashMap<String, Object>();
    Class<?> type = field.getType();

    // Note: Nullable fields will be excluded from JSON by ObjectMapper.setSerializationInclusion(NON_NULL)
    // So we just define the type normally and rely on the field not being in "required" array
    if (type == String.class) {
      schema.put("type", STRING);
    } else if (type == Integer.class || type == int.class ||
               type == Long.class || type == long.class ||
               type == Double.class || type == double.class) {
      schema.put("type", NUMBER);
    } else if (type == Boolean.class || type == boolean.class) {
      schema.put("type", BOOLEAN);
    } else if (List.class.isAssignableFrom(field.getType())) {
      schema.put("type", "array");
      if (field.getGenericType() instanceof ParameterizedType paramType) {
        var typeArgs = paramType.getActualTypeArguments();
        if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> itemClass) {
          schema.put(ITEMS, generateItemSchema(itemClass));
        }
      } else {
        schema.put(ITEMS, Map.of("type", OBJECT));
      }
    } else {
      // Nested object
      var nestedSchema = generateOutputSchema(type);
      schema.putAll(nestedSchema);
    }

    var description = getDescriptionFromField(field);
    schema.put("description", description);

    return schema;
  }

  /**
   * Generate schema for array items.
   */
  private static Map<String, Object> generateItemSchema(Class<?> itemClass) {
    if (itemClass == String.class) {
      return Map.of("type", STRING);
    } else if (Number.class.isAssignableFrom(itemClass) || 
               itemClass == int.class || itemClass == long.class || itemClass == double.class) {
      return Map.of("type", NUMBER);
    } else if (itemClass == Boolean.class || itemClass == boolean.class) {
      return Map.of("type", BOOLEAN);
    } else {
      // Nested object - recursively generate
      return generateOutputSchema(itemClass);
    }
  }

  /**
   * Check if a record component is nullable.
   * Supports  javax.annotation.Nullable (requires RUNTIME annotation)
   */
  private static boolean isNullable(RecordComponent component) {
    for (var annotation : component.getAnnotations()) {
      var annotationName = annotation.annotationType().getSimpleName();
      if (annotationName.equals("Nullable")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if a field is nullable.
   * Supports  javax.annotation.Nullable (requires RUNTIME annotation)
   */
  private static boolean isNullable(Field field) {
    for (var annotation : field.getAnnotations()) {
      var annotationName = annotation.annotationType().getSimpleName();
      if (annotationName.equals("Nullable")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get description from @Description annotation or generate from component name.
   */
  private static String getDescriptionFromComponent(RecordComponent component) {
    var descAnnotation = component.getAnnotation(Description.class);
    if (descAnnotation != null) {
      return descAnnotation.value();
    }
    return generateDescriptionFromFieldName(component.getName());
  }

  /**
   * Get description from @Description annotation or generate from field name.
   */
  private static String getDescriptionFromField(Field field) {
    var descAnnotation = field.getAnnotation(Description.class);
    if (descAnnotation != null) {
      return descAnnotation.value();
    }
    return generateDescriptionFromFieldName(field.getName());
  }

  /**
   * Generate a human-readable description from a camelCase field name.
   */
  private static String generateDescriptionFromFieldName(String fieldName) {
    return fieldName.replaceAll("([A-Z])", " $1").toLowerCase(Locale.getDefault()).trim();
  }

  /**
   * Serialize an object to a Map for use as structured content.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> toStructuredContent(Object obj) {
    return OBJECT_MAPPER.convertValue(obj, Map.class);
  }

  /**
   * Convert a response object to JSON string for text content.
   * According to MCP spec, structured content should also be available as text (serialized JSON).
   */
  public static String toJsonString(Object response) {
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to convert response to JSON string", e);
    }
  }

}

