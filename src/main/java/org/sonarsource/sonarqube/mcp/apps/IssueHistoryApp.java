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
            .chart-widget {
              border: 1px solid #d8dee9;
              background: #ffffff;
              padding: 16px;
            }
            .chart-frame {
              position: relative;
              height: 320px;
              min-height: 260px;
              min-width: 0;
              width: 100%;
            }
            svg {
              display: block;
              height: 100%;
              min-height: 0;
              min-width: 0;
              width: 100%;
            }
            .axis {
              stroke: #e0e0e0;
              stroke-width: 1;
            }
            .grid-line {
              opacity: 0.075;
              stroke: currentColor;
              stroke-dasharray: 4,1;
              stroke-width: 1;
            }
            .tick-label {
              fill: #64748b;
              font-size: 12px;
            }
            .series-line {
              fill: none;
              stroke-linecap: round;
              stroke-linejoin: round;
              stroke-width: 2;
            }
            .series-dot {
              stroke: #ffffff;
              stroke-width: 2;
            }
            .legend {
              display: flex;
              flex-wrap: wrap;
              gap: 8px 16px;
              margin-top: 12px;
            }
            .legend-item {
              align-items: center;
              color: #334155;
              display: inline-flex;
              font-size: 12px;
              gap: 6px;
              min-width: 0;
            }
            .legend-swatch {
              border-radius: 999px;
              display: inline-block;
              height: 10px;
              width: 10px;
            }
            .tooltip {
              background: #172033;
              border-radius: 6px;
              color: #ffffff;
              display: none;
              font-size: 12px;
              max-width: 260px;
              padding: 8px 10px;
              pointer-events: none;
              position: absolute;
              z-index: 1;
            }
            .tooltip strong {
              display: block;
              margin-bottom: 4px;
            }
            .tooltip-row {
              align-items: center;
              display: flex;
              gap: 6px;
              justify-content: space-between;
            }
            .tooltip-swatch {
              border-radius: 999px;
              display: inline-block;
              height: 8px;
              width: 8px;
            }
            .empty {
              color: #64748b;
              margin: 16px 0 0;
            }
            @media (prefers-color-scheme: dark) {
              body {
                background: #111827;
                color: #f8fafc;
              }
              .chart-widget {
                border-color: #334155;
                background: #1f2937;
              }
              .legend-item,
              .tick-label {
                color: #cbd5e1;
                fill: #cbd5e1;
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
            <section class="chart-widget" aria-label="Issue history by bucket">
              <div class="chart-frame" id="chart-frame">
                <svg id="issue-history-chart" role="img" aria-label="Issue history by bucket"></svg>
                <div class="tooltip" id="chart-tooltip" role="tooltip"></div>
              </div>
              <div class="legend" id="chart-legend"></div>
              <p class="empty" id="empty-state" hidden>No issue history returned.</p>
            </section>
          </main>
          <script>
            (function () {
              var initialIssueHistory = """ + renderIssueHistoryJson(response) + ";\n" + """
              var currentIssueHistory = initialIssueHistory;
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

              function createSvgElement(name, attributes) {
                var element = document.createElementNS("http://www.w3.org/2000/svg", name);
                Object.keys(attributes || {}).forEach(function (key) {
                  element.setAttribute(key, attributes[key]);
                });
                return element;
              }

              function formatDate(value) {
                return new Date(value).toLocaleString(undefined, { month: "short", day: "numeric" });
              }

              function formatValue(value) {
                return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value);
              }

              function niceCeiling(value) {
                if (value <= 0) {
                  return 1;
                }
                var roughStep = value / 5;
                var magnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
                var normalized = roughStep / magnitude;
                var step = magnitude;
                if (normalized > 5) {
                  step = 10 * magnitude;
                } else if (normalized > 2) {
                  step = 5 * magnitude;
                } else if (normalized > 1) {
                  step = 2 * magnitude;
                }
                return Math.ceil(value / step) * step;
              }

              function buildSeries(history) {
                var byKey = {};
                if (!Array.isArray(history)) {
                  return [];
                }

                history.forEach(function (item) {
                  var timestamp = new Date(item.date).getTime();
                  if (!Number.isFinite(timestamp)) {
                    return;
                  }
                  var distribution = Array.isArray(item.distribution) && item.distribution.length > 0
                    ? item.distribution
                    : [{ key: "No bucket", value: 0 }];
                  distribution.forEach(function (bucket) {
                    var value = Number(bucket.value);
                    if (!Number.isFinite(value)) {
                      return;
                    }
                    var key = String(bucket.key || "all");
                    if (!byKey[key]) {
                      byKey[key] = [];
                    }
                    byKey[key].push({ x: timestamp, y: value, date: item.date });
                  });
                });

                return Object.keys(byKey).sort().map(function (key, index) {
                  return {
                    color: ["#2d9cdb", "#27ae60", "#f2c94c", "#eb5757", "#9b51e0", "#172033", "#f2994a", "#56ccf2"][index % 8],
                    key: key,
                    points: byKey[key].sort(function (left, right) {
                      return left.x - right.x;
                    })
                  };
                }).filter(function (series) {
                  return series.points.length > 0;
                });
              }

              function renderIssueHistory(history) {
                currentIssueHistory = history;
                var frame = document.getElementById("chart-frame");
                var svg = document.getElementById("issue-history-chart");
                var legend = document.getElementById("chart-legend");
                var emptyState = document.getElementById("empty-state");
                var tooltip = document.getElementById("chart-tooltip");
                if (!frame || !svg || !legend || !emptyState || !tooltip) {
                  return;
                }

                var series = buildSeries(history);
                svg.innerHTML = "";
                legend.innerHTML = "";
                tooltip.style.display = "none";

                if (series.length === 0) {
                  emptyState.hidden = false;
                  notifySizeChanged();
                  return;
                }
                emptyState.hidden = true;

                var width = Math.max(frame.clientWidth || 0, 520);
                var height = Math.max(frame.clientHeight || 0, 300);
                var padding = { top: 16, right: 24, bottom: 42, left: 58 };
                var availableWidth = width - padding.left - padding.right;
                var availableHeight = height - padding.top - padding.bottom;
                var allPoints = [];
                series.forEach(function (item) {
                  item.points.forEach(function (point) {
                    allPoints.push(point);
                  });
                });
                var xMin = Math.min.apply(null, allPoints.map(function (point) { return point.x; }));
                var xMax = Math.max.apply(null, allPoints.map(function (point) { return point.x; }));
                var yMax = niceCeiling(Math.max.apply(null, allPoints.map(function (point) { return point.y; })));
                if (xMin === xMax) {
                  xMin -= 24 * 60 * 60 * 1000;
                  xMax += 24 * 60 * 60 * 1000;
                }

                function xScale(value) {
                  return padding.left + ((value - xMin) / (xMax - xMin)) * availableWidth;
                }

                function yScale(value) {
                  return padding.top + ((yMax - value) / yMax) * availableHeight;
                }

                function append(element) {
                  svg.appendChild(element);
                }

                svg.setAttribute("viewBox", "0 0 " + width + " " + height);
                append(createSvgElement("line", { class: "axis", x1: padding.left, x2: padding.left, y1: padding.top, y2: padding.top + availableHeight }));
                append(createSvgElement("line", { class: "axis", x1: padding.left, x2: padding.left + availableWidth, y1: padding.top + availableHeight, y2: padding.top + availableHeight }));

                for (var i = 0; i <= 5; i++) {
                  var tick = (yMax / 5) * i;
                  var y = yScale(tick);
                  append(createSvgElement("line", { class: "grid-line", x1: padding.left, x2: padding.left + availableWidth, y1: y, y2: y }));
                  var yLabel = createSvgElement("text", { class: "tick-label", "text-anchor": "end", x: padding.left - 10, y: y + 4 });
                  yLabel.textContent = formatValue(tick);
                  append(yLabel);
                }

                var uniqueDates = Array.from(new Set(allPoints.map(function (point) { return point.x; }))).sort(function (left, right) { return left - right; });
                var xTickCount = Math.min(6, uniqueDates.length);
                var xTicks = uniqueDates.filter(function (_date, index) {
                  return xTickCount <= 1 || index % Math.max(1, Math.ceil(uniqueDates.length / xTickCount)) === 0 || index === uniqueDates.length - 1;
                });
                xTicks.forEach(function (tick) {
                  var x = xScale(tick);
                  append(createSvgElement("line", { class: "axis", x1: x, x2: x, y1: padding.top + availableHeight, y2: padding.top + availableHeight + 5 }));
                  var xLabel = createSvgElement("text", { class: "tick-label", "text-anchor": "middle", x: x, y: padding.top + availableHeight + 24 });
                  xLabel.textContent = formatDate(tick);
                  append(xLabel);
                });

                series.forEach(function (item) {
                  var path = item.points.map(function (point, index) {
                    return (index === 0 ? "M" : "L") + xScale(point.x) + "," + yScale(point.y);
                  }).join(" ");
                  append(createSvgElement("path", { class: "series-line", d: path, stroke: item.color }));
                  item.points.forEach(function (point) {
                    append(createSvgElement("circle", {
                      class: "series-dot",
                      cx: xScale(point.x),
                      cy: yScale(point.y),
                      fill: item.color,
                      r: 3
                    }));
                  });
                  var legendItem = document.createElement("span");
                  legendItem.className = "legend-item";
                  legendItem.innerHTML = '<span class="legend-swatch" style="background:' + item.color + '"></span>' + escapeHtml(item.key);
                  legend.appendChild(legendItem);
                });

                svg.onmousemove = function (event) {
                  var rect = svg.getBoundingClientRect();
                  var scaledX = padding.left + ((event.clientX - rect.left) / rect.width) * width - padding.left;
                  var clampedX = Math.max(0, Math.min(scaledX, availableWidth));
                  var hoveredTimestamp = xMin + (clampedX / availableWidth) * (xMax - xMin);
                  var nearestDate = uniqueDates.reduce(function (nearest, date) {
                    return Math.abs(date - hoveredTimestamp) < Math.abs(nearest - hoveredTimestamp) ? date : nearest;
                  }, uniqueDates[0]);
                  var rows = series.map(function (item) {
                    var point = item.points.find(function (candidate) {
                      return candidate.x === nearestDate;
                    });
                    if (!point) {
                      return "";
                    }
                    return '<span class="tooltip-row"><span><span class="tooltip-swatch" style="background:' + item.color + '"></span>' +
                      escapeHtml(item.key) + '</span><span>' + formatValue(point.y) + "</span></span>";
                  }).join("");
                  tooltip.innerHTML = "<strong>" + escapeHtml(formatDate(nearestDate)) + "</strong>" + rows;
                  tooltip.style.display = "block";
                  tooltip.style.left = Math.min(event.clientX - rect.left + 12, frame.clientWidth - tooltip.offsetWidth - 8) + "px";
                  tooltip.style.top = Math.max(event.clientY - rect.top - tooltip.offsetHeight - 12, 8) + "px";
                };
                svg.onmouseleave = function () {
                  tooltip.style.display = "none";
                };
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

              window.requestAnimationFrame(function () {
                renderIssueHistory(currentIssueHistory);
              });
              window.addEventListener("load", function () {
                renderIssueHistory(currentIssueHistory);
              });
              window.addEventListener("resize", function () {
                renderIssueHistory(currentIssueHistory);
              });
              initialize(0);
            })();
          </script>
        </body>
      </html>
      """;
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
