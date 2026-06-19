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
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.tools.issues.GetIssueCountHistoryToolResponse;

public class IssueHistoryApp {

  public static final String RESOURCE_URI = "ui://sonarqube/issue-history.html";
  public static final String RESOURCE_NAME = "sonarqube_issue_history";
  public static final String RESOURCE_TITLE = "SonarQube Issue History";
  public static final String RESOURCE_DESCRIPTION = "A table view of SonarQube issue count history.";
  public static final String MIME_TYPE = "text/html;profile=mcp-app";
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
    return """
      <!doctype html>
      <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>SonarQube Issue History</title>
          <style>
            :root {
              color-scheme: light dark;
              font-family: Arial, Helvetica, sans-serif;
            }
            body {
              margin: 0;
              background: #f8fafc;
              color: #172033;
            }
            main {
              padding: 24px;
            }
            h1 {
              margin: 0 0 16px;
              font-size: 24px;
              line-height: 1.2;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              border: 1px solid #d8dee9;
              background: #ffffff;
            }
            th,
            td {
              border-bottom: 1px solid #e2e8f0;
              padding: 10px 12px;
              text-align: left;
              font-size: 14px;
              line-height: 1.35;
            }
            th {
              background: #eef2f7;
              color: #334155;
              font-weight: 700;
            }
            td:last-child {
              font-variant-numeric: tabular-nums;
            }
            .empty {
              color: #64748b;
            }
            @media (prefers-color-scheme: dark) {
              body {
                background: #111827;
                color: #f8fafc;
              }
              table {
                border-color: #334155;
                background: #1f2937;
              }
              th {
                background: #0f172a;
                color: #cbd5e1;
              }
              th,
              td {
                border-bottom-color: #334155;
              }
              .empty {
                color: #94a3b8;
              }
            }
          </style>
        </head>
        <body>
          <main>
            <h1>Issue History</h1>
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Bucket</th>
                  <th>Issue Count</th>
                </tr>
              </thead>
              <tbody id="rows">
      """ + renderRows(response) + """
              </tbody>
            </table>
          </main>
          <script>
            (function () {
              var host = window.parent;
              var nextId = 1;
              var pending = new Map();
              var initializeProtocolVersions = ["2025-11-25", "2025-06-18", "2024-11-05"];

              function send(message) {
                host.postMessage(message, "*");
              }

              function request(method, params, timeoutMs) {
                var id = nextId++;
                send({ jsonrpc: "2.0", id: id, method: method, params: params });
                return new Promise(function (resolve, reject) {
                  var timeout = window.setTimeout(function () {
                    pending.delete(id);
                    reject(new Error(method + " timed out"));
                  }, timeoutMs);
                  pending.set(id, { resolve: resolve, reject: reject, timeout: timeout });
                });
              }

              function notify(method, params) {
                send({ jsonrpc: "2.0", method: method, params: params });
              }

              function notifySizeChanged() {
                notify("ui/notifications/size-changed", { height: document.body.scrollHeight || 1 });
              }

              function escapeHtml(value) {
                return String(value == null ? "" : value)
                  .replace(/&/g, "&amp;")
                  .replace(/</g, "&lt;")
                  .replace(/>/g, "&gt;")
                  .replace(/"/g, "&quot;");
              }

              function renderIssueHistory(history) {
                var rows = document.getElementById("rows");
                if (!rows) {
                  return;
                }
                if (!Array.isArray(history) || history.length === 0) {
                  rows.innerHTML = '<tr><td colspan="3" class="empty">No issue history returned.</td></tr>';
                  notifySizeChanged();
                  return;
                }

                rows.innerHTML = history.map(function (item) {
                  if (!Array.isArray(item.distribution) || item.distribution.length === 0) {
                    return "<tr><td>" + escapeHtml(item.date) +
                      '</td><td class="empty">No bucket</td><td>0</td></tr>';
                  }
                  return item.distribution.map(function (bucket) {
                    return "<tr><td>" + escapeHtml(item.date) +
                      "</td><td>" + escapeHtml(bucket.key) +
                      "</td><td>" + escapeHtml(bucket.value) +
                      "</td></tr>";
                  }).join("");
                }).join("");
                notifySizeChanged();
              }

              function issueHistoryFrom(source) {
                var structured = source && (source.structuredContent ||
                  (source.result && source.result.structuredContent) ||
                  (source.data && source.data.structuredContent) ||
                  (source.toolResult && source.toolResult.structuredContent));
                if (structured && structured.issueHistory && structured.issueHistory.issueCountHistory) {
                  return structured.issueHistory.issueCountHistory;
                }
                if (structured && structured.issueCountHistory) {
                  return structured.issueCountHistory;
                }
                if (structured && structured.history) {
                  return structured.history.map(function (item) {
                    return {
                      date: item.date,
                      distribution: [{ key: item.bucket || "all", value: item.count }]
                    };
                  });
                }
                return null;
              }

              function hydrateFromMessage(message) {
                var history = issueHistoryFrom(message.params) || issueHistoryFrom(message);
                if (history) {
                  renderIssueHistory(history);
                }
              }

              function initialize(index) {
                if (index >= initializeProtocolVersions.length) {
                  console.error("ui/initialize failed for all protocol versions");
                  notify("ui/notifications/initialized");
                  notifySizeChanged();
                  return;
                }

                request("ui/initialize", {
                  protocolVersion: initializeProtocolVersions[index],
                  appInfo: {
                    name: "sonarqube_issue_history",
                    title: "SonarQube Issue History",
                    version: "1.0.0"
                  },
                  appCapabilities: {}
                }, 2000)
                  .then(function () {
                    notify("ui/notifications/initialized");
                    notifySizeChanged();
                  })
                  .catch(function (error) {
                    console.warn("ui/initialize failed:", error);
                    initialize(index + 1);
                  });
              }

              window.addEventListener("message", function (event) {
                var message = event.data;
                if (!message) {
                  return;
                }

                if (message.jsonrpc !== "2.0") {
                  hydrateFromMessage(message);
                  return;
                }

                if (message.id !== undefined && pending.has(message.id)) {
                  var response = pending.get(message.id);
                  pending.delete(message.id);
                  window.clearTimeout(response.timeout);
                  if (message.error) {
                    response.reject(message.error);
                  } else {
                    response.resolve(message.result);
                  }
                  return;
                }

                hydrateFromMessage(message);
              });

              window.requestAnimationFrame(notifySizeChanged);
              window.addEventListener("load", notifySizeChanged);
              initialize(0);
            })();
          </script>
        </body>
      </html>
      """;
  }

  private static String renderRows(GetIssueCountHistoryToolResponse response) {
    var history = response.issueCountHistory();
    if (history == null || history.isEmpty()) {
      return """
                <tr>
                  <td colspan="3" class="empty">No issue history returned.</td>
                </tr>
        """;
    }

    var rows = new StringBuilder();
    for (var item : history) {
      var distribution = item.distribution();
      if (distribution == null || distribution.isEmpty()) {
        rows.append("""
                  <tr>
                    <td>%s</td>
                    <td class="empty">No bucket</td>
                    <td>0</td>
                  </tr>
          """.formatted(escape(item.date())));
      } else {
        for (var bucket : distribution) {
          rows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td>%s</td>
                    </tr>
            """.formatted(escape(item.date()), escape(bucket.key()), bucket.value() == null ? "" : bucket.value()));
        }
      }
    }
    return rows.toString();
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

}
