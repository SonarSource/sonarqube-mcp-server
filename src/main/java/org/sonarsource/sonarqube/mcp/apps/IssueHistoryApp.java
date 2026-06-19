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
import org.sonarsource.sonarqube.mcp.tools.issues.GetIssueCountHistoryToolResponse;

public class IssueHistoryApp {

  public static final String RESOURCE_URI = "ui://sonarqube/issue-history.html";
  public static final String RESOURCE_NAME = "sonarqube_issue_history";
  public static final String RESOURCE_TITLE = "SonarQube Issue History";
  public static final String RESOURCE_DESCRIPTION = "A visualization tool for SonarQube issue count history.";
  public static final String MIME_TYPE = "text/html;profile=mcp-app";
  private static final String RESOURCE_PATH = "/mcp-apps/issue-history.html";
  private static final String APP_SCRIPT_PATH = "/mcp-apps/issue-history-app.js";
  private static final String APP_STYLE_PATH = "/mcp-apps/issue-history-app.css";
  private static final String APP_SCRIPT_PLACEHOLDER = "__ISSUE_HISTORY_APP_JS__";
  private static final String APP_STYLE_PLACEHOLDER = "__ISSUE_HISTORY_APP_CSS__";
  private static final String INITIAL_HISTORY_PLACEHOLDER = "__ISSUE_HISTORY_JSON__";
  private static final GetIssueCountHistoryToolResponse EMPTY_RESPONSE = new GetIssueCountHistoryToolResponse(List.of());
  private static volatile GetIssueCountHistoryToolResponse latestResponse = EMPTY_RESPONSE;

  private IssueHistoryApp() {
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

  public static McpSchema.EmbeddedResource embeddedResource(GetIssueCountHistoryToolResponse response) {
    return new McpSchema.EmbeddedResource(null, textResourceContents(renderHtml(response)));
  }

  public static McpSchema.ReadResourceResult readResource() {
    return new McpSchema.ReadResourceResult(List.of(textResourceContents(renderHtml(latestResponse))));
  }

  public static void remember(GetIssueCountHistoryToolResponse response) {
    latestResponse = response == null ? EMPTY_RESPONSE : response;
  }

  public static Map<String, Object> toolDescriptorMeta() {
    return Map.of(
      "ui", Map.of(
        "resourceUri", RESOURCE_URI,
        "visibility", List.of("model", "app")
      ),
      "openai/outputTemplate", RESOURCE_URI,
      "openai/widgetAccessible", true,
      "openai/toolInvocation/invoking", "Loading issue history",
      "openai/toolInvocation/invoked", "Issue history loaded"
    );
  }

  private static McpSchema.TextResourceContents textResourceContents(String html) {
    return new McpSchema.TextResourceContents(RESOURCE_URI, MIME_TYPE, html, resourceContentMeta());
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

  private static String renderHtml(GetIssueCountHistoryToolResponse response) {
    return readResourceText(RESOURCE_PATH)
      .replace(INITIAL_HISTORY_PLACEHOLDER, renderIssueHistoryJson(response))
      .replace(APP_STYLE_PLACEHOLDER, readResourceText(APP_STYLE_PATH))
      .replace(APP_SCRIPT_PLACEHOLDER, readResourceText(APP_SCRIPT_PATH));
  }

  private static String readResourceText(String resourcePath) {
    try (var inputStream = IssueHistoryApp.class.getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing MCP app resource: " + resourcePath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read MCP app resource: " + resourcePath, e);
    }
  }

  private static String renderIssueHistoryJson(GetIssueCountHistoryToolResponse response) {
    var history = response.issueCountHistory();
    if (history == null || history.isEmpty()) {
      return "[]";
    }

    var items = new StringBuilder("[");
    for (var i = 0; i < history.size(); i++) {
      var item = history.get(i);
      if (i > 0) {
        items.append(',');
      }
      items.append("{\"date\":\"")
        .append(escapeJson(item.date()))
        .append("\",\"distribution\":[");

      var distribution = item.distribution();
      if (distribution != null) {
        for (var j = 0; j < distribution.size(); j++) {
          var bucket = distribution.get(j);
          if (j > 0) {
            items.append(',');
          }
          items.append("{\"key\":\"")
            .append(escapeJson(bucket.key()))
            .append("\",\"value\":");
          if (bucket.value() == null) {
            items.append("null");
          } else {
            items.append(bucket.value());
          }
          items.append('}');
        }
      }
      items.append("]}");
    }
    return items.append(']').toString();
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("</", "<\\/");
  }

}
