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
package org.sonarsource.sonarqube.mcp.tools.measures;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class GetComponentMeasuresTool extends Tool {

  public static final String TOOL_NAME = "get_component_measures";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String METRIC_KEYS_PROPERTY = "metricKeys";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";

  private final ServerApiProvider serverApiProvider;

  public GetComponentMeasuresTool(ServerApiProvider serverApiProvider) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Project Measures")
      .setDescription("Get SonarQube measures for a project, such as ncloc, complexity, violations, coverage, etc.")
      .addStringProperty(PROJECT_KEY_PROPERTY, "The project key")
      .addStringProperty(BRANCH_PROPERTY, "The branch to analyze for measures")
      .addArrayProperty(METRIC_KEYS_PROPERTY, "string", "The metric keys to retrieve (e.g. ncloc, complexity, violations, coverage)")
      .addStringProperty(PULL_REQUEST_PROPERTY, "The pull request identifier to analyze for measures")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var component = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var metricKeys = arguments.getOptionalStringList(METRIC_KEYS_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);
    
    var response = serverApiProvider.get().measuresApi().getComponentMeasures(component, branch, metricKeys, pullRequest);
    return Tool.Result.success(buildResponseFromComponentMeasures(response));
  }

  private static String buildResponseFromComponentMeasures(ComponentMeasuresResponse response) {
    var stringBuilder = new StringBuilder();
    var component = response.component();
    var metrics = response.metrics();
    var periods = response.periods();

    if (component == null) {
      stringBuilder.append("No project key found.");
      return stringBuilder.toString();
    }

    appendComponentInfo(stringBuilder, component);
    appendMeasuresInfo(stringBuilder, component, metrics);
    appendMetricsInfo(stringBuilder, metrics);
    appendPeriodsInfo(stringBuilder, periods);

    return stringBuilder.toString().trim();
  }

  private static void appendComponentInfo(StringBuilder stringBuilder, ComponentMeasuresResponse.Component component) {
    stringBuilder.append("Component: ").append(component.name()).append("\n");
    stringBuilder.append("Key: ").append(component.key()).append("\n");
    if (component.description() != null) {
      stringBuilder.append("Description: ").append(component.description()).append("\n");
    }
    stringBuilder.append("Qualifier: ").append(component.qualifier()).append("\n");
    if (component.language() != null) {
      stringBuilder.append("Language: ").append(component.language()).append("\n");
    }
    if (component.path() != null) {
      stringBuilder.append("Path: ").append(component.path()).append("\n");
    }
    stringBuilder.append("\n");
  }

  private static void appendMeasuresInfo(StringBuilder stringBuilder, ComponentMeasuresResponse.Component component,
    List<ComponentMeasuresResponse.Metric> metrics) {
    var measures = component.measures();
    if (measures == null || measures.isEmpty()) {
      stringBuilder.append("No measures found for this component.");
      return;
    }

    stringBuilder.append("Measures:\n");
    for (var measure : measures) {
      appendMeasureInfo(stringBuilder, measure, metrics);
    }
  }

  private static void appendMeasureInfo(StringBuilder stringBuilder, ComponentMeasuresResponse.Measure measure,
    List<ComponentMeasuresResponse.Metric> metrics) {
    var metric = findMetricByKey(metrics, measure.metric());
    if (metric != null) {
      stringBuilder.append("  - ").append(metric.name()).append(" (").append(measure.metric()).append("): ");
      appendMeasureValue(stringBuilder, measure);
      stringBuilder.append("\n");
      if (metric.description() != null) {
        stringBuilder.append("    Description: ").append(metric.description()).append("\n");
      }
    } else {
      stringBuilder.append("  - ").append(measure.metric()).append(": ").append(measure.value()).append("\n");
    }
  }

  private static void appendMeasureValue(StringBuilder stringBuilder, ComponentMeasuresResponse.Measure measure) {
    if (measure.value() != null) {
      stringBuilder.append(measure.value());
    }
    appendMeasurePeriods(stringBuilder, measure);
  }

  private static void appendMeasurePeriods(StringBuilder stringBuilder, ComponentMeasuresResponse.Measure measure) {
    if (measure.periods() != null && !measure.periods().isEmpty()) {
      stringBuilder.append(" | New: ");
      for (var period : measure.periods()) {
        stringBuilder.append(period.value());
      }
    }
  }

  private static void appendMetricsInfo(StringBuilder stringBuilder, List<ComponentMeasuresResponse.Metric> metrics) {
    if (metrics != null && !metrics.isEmpty()) {
      stringBuilder.append("\nAvailable Metrics:\n");
      for (var metric : metrics) {
        appendMetricInfo(stringBuilder, metric);
      }
    }
  }

  private static void appendMetricInfo(StringBuilder stringBuilder, ComponentMeasuresResponse.Metric metric) {
    stringBuilder.append("  - ").append(metric.name()).append(" (").append(metric.key()).append(")\n");
    stringBuilder.append("    Description: ").append(metric.description()).append("\n");
    stringBuilder.append("    Domain: ").append(metric.domain()).append("\n");
    stringBuilder.append("    Type: ").append(metric.type()).append("\n");
    stringBuilder.append("    Higher values are better: ").append(metric.higherValuesAreBetter()).append("\n");
    stringBuilder.append("    Qualitative: ").append(metric.qualitative()).append("\n");
    stringBuilder.append("    Hidden: ").append(metric.hidden()).append("\n");
    stringBuilder.append("    Custom: ").append(metric.custom()).append("\n");
    stringBuilder.append("\n");
  }

  private static void appendPeriodsInfo(StringBuilder stringBuilder, List<ComponentMeasuresResponse.Period> periods) {
    if (periods != null && !periods.isEmpty()) {
      stringBuilder.append("Periods:\n");
      for (var period : periods) {
        appendPeriodInfo(stringBuilder, period);
      }
    }
  }

  private static void appendPeriodInfo(StringBuilder stringBuilder, ComponentMeasuresResponse.Period period) {
    stringBuilder.append("  - Period ").append(period.index()).append(": ").append(period.mode());
    if (period.date() != null) {
      stringBuilder.append(" (").append(period.date()).append(")");
    }
    if (period.parameter() != null) {
      stringBuilder.append(" - ").append(period.parameter());
    }
    stringBuilder.append("\n");
  }

  private static ComponentMeasuresResponse.Metric findMetricByKey(List<ComponentMeasuresResponse.Metric> metrics, String key) {
    if (metrics == null) {
      return null;
    }
    return metrics.stream()
      .filter(metric -> metric.key().equals(key))
      .findFirst()
      .orElse(null);
  }

} 
