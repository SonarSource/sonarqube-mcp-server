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
package org.sonarsource.sonarqube.mcp.apps;

import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class HelloWorldApp {

  public static final String RESOURCE_URI = "ui://sonarqube/hello-world.html";
  public static final String RESOURCE_NAME = "sonarqube_hello_world";
  public static final String RESOURCE_TITLE = "SonarQube Color Picker";
  public static final String RESOURCE_DESCRIPTION = "A minimal interactive color picker MCP UI resource.";
  public static final String MIME_TYPE = "text/html;profile=mcp-app";
  private static final String RESOURCE_PATH = "/mcp-apps/hello-world.html";

  private HelloWorldApp() {
    // Utility class
  }

  public static McpSchema.Resource resource() {
    return McpSchema.Resource.builder()
      .uri(RESOURCE_URI)
      .name(RESOURCE_NAME)
      .title(RESOURCE_TITLE)
      .description(RESOURCE_DESCRIPTION)
      .mimeType(MIME_TYPE)
      .build();
  }

  public static McpSchema.ResourceLink resourceLink() {
    return McpSchema.ResourceLink.builder()
      .uri(RESOURCE_URI)
      .name(RESOURCE_NAME)
      .title(RESOURCE_TITLE)
      .description(RESOURCE_DESCRIPTION)
      .mimeType(MIME_TYPE)
      .build();
  }

  public static McpSchema.EmbeddedResource embeddedResource() {
    return new McpSchema.EmbeddedResource(null, textResourceContents());
  }

  public static McpSchema.ReadResourceResult readResource() {
    return new McpSchema.ReadResourceResult(List.of(textResourceContents()));
  }

  public static Map<String, Object> toolDescriptorMeta() {
    return Map.of(
      "ui", Map.of(
        "resourceUri", RESOURCE_URI,
        "visibility", List.of("model", "app")
      ),
      "openai/outputTemplate", RESOURCE_URI,
      "openai/widgetAccessible", true,
      "openai/toolInvocation/invoking", "Opening app",
      "openai/toolInvocation/invoked", "App ready"
    );
  }

  private static Map<String, Object> resourceContentMeta() {
    var standardCsp = Map.of(
      "connectDomains", List.of(),
      "resourceDomains", List.of()
    );
    var legacyCsp = Map.of(
      "connect_domains", List.of(),
      "resource_domains", List.of()
    );
    return Map.of(
      "ui", Map.of(
        "prefersBorder", true,
        "csp", standardCsp
      ),
      "openai/widgetDescription", RESOURCE_DESCRIPTION,
      "openai/widgetPrefersBorder", true,
      "openai/widgetCSP", legacyCsp
    );
  }

  private static McpSchema.TextResourceContents textResourceContents() {
    return new McpSchema.TextResourceContents(RESOURCE_URI, MIME_TYPE, readHtml(), resourceContentMeta());
  }

  private static String readHtml() {
    try (var inputStream = HelloWorldApp.class.getResourceAsStream(RESOURCE_PATH)) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing MCP app resource: " + RESOURCE_PATH);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read MCP app resource: " + RESOURCE_PATH, e);
    }
  }

}
