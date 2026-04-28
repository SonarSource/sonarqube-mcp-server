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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListQualityGatesTool extends Tool {

  public static final String TOOL_NAME = "list_quality_gates";
  public static final String ORGANIZATION_PROPERTY = "organization";

  private final ServerApiProvider serverApiProvider;
  private final boolean isSonarQubeCloud;
  @Nullable
  private final String configuredOrganization;

  public ListQualityGatesTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredOrganization) {
    super(buildSchema(isSonarQubeCloud, configuredOrganization), ToolCategory.QUALITY_GATES);
    this.serverApiProvider = serverApiProvider;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.configuredOrganization = configuredOrganization;
  }

  private static io.modelcontextprotocol.spec.McpSchema.Tool buildSchema(boolean isSonarQubeCloud, @Nullable String configuredOrganization) {
    var builder = SchemaToolBuilder.forOutput(ListQualityGatesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Quality Gates")
      .setDescription("List all quality gates.");

    if (isSonarQubeCloud) {
      builder.addOrganizationProperty(ORGANIZATION_PROPERTY,
        "The SonarQube Cloud organization key. Required when SONARQUBE_ORG is not configured at the server level. "
          + "Use list_sonarqube_organizations to discover available keys.",
        configuredOrganization);
    }

    return builder.setReadOnlyHint().build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var orgOverride = isSonarQubeCloud
      ? arguments.getOrganizationWithFallback(ORGANIZATION_PROPERTY, configuredOrganization)
      : null;
    var response = serverApiProvider.get(orgOverride).qualityGatesApi().list();
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static ListQualityGatesToolResponse buildStructuredContent(ListResponse response) {
    var qualityGates = response.qualitygates().stream()
      .map(gate -> {
        var conditions = (gate.conditions() != null)
          ? gate.conditions().stream()
              .map(c -> new ListQualityGatesToolResponse.Condition(c.metric(), c.op(), c.error()))
              .toList()
          : null;
        
        return new ListQualityGatesToolResponse.QualityGate(
          gate.id(),
          gate.name(),
          gate.isDefault(),
          gate.isBuiltIn(),
          conditions,
          gate.caycStatus(),
          gate.hasStandardConditions(),
          gate.hasMQRConditions(),
          gate.isAiCodeSupported()
        );
      })
      .toList();

    return new ListQualityGatesToolResponse(qualityGates);
  }

}
