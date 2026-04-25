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
package org.sonarsource.sonarqube.mcp.tools.rules;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.ShowResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ShowRuleTool extends Tool {

  public static final String TOOL_NAME = "show_rule";
  public static final String KEY_PROPERTY = "key";
  public static final String ORGANIZATION_PROPERTY = "organization";

  private final ServerApiProvider serverApiProvider;
  private final boolean isSonarQubeCloud;
  @Nullable
  private final String configuredOrganization;

  public ShowRuleTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredOrganization) {
    super(buildSchema(isSonarQubeCloud, configuredOrganization), ToolCategory.RULES);
    this.serverApiProvider = serverApiProvider;
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.configuredOrganization = configuredOrganization;
  }

  private static McpSchema.Tool buildSchema(boolean isSonarQubeCloud, @Nullable String configuredOrganization) {
    var builder = SchemaToolBuilder.forOutput(ShowRuleToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Show SonarQube Rule Details")
      .setDescription("Shows detailed information about a SonarQube rule.")
      .addRequiredStringProperty(KEY_PROPERTY, "The rule key (e.g. javascript:EmptyBlock)");

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
    var ruleKey = arguments.getStringOrThrow(KEY_PROPERTY);
    var orgOverride = isSonarQubeCloud
      ? arguments.getOrganizationWithFallback(ORGANIZATION_PROPERTY, configuredOrganization)
      : null;
    var response = serverApiProvider.get(orgOverride).rulesApi().showRule(ruleKey);
    var toolResponse = buildStructuredContent(response.rule());
    return Tool.Result.success(toolResponse);
  }

  private static ShowRuleToolResponse buildStructuredContent(ShowResponse.Rule rule) {
    var impacts = (rule.impacts() != null && !rule.impacts().isEmpty())
      ? rule.impacts().stream()
          .map(i -> new ShowRuleToolResponse.Impact(i.softwareQuality(), i.severity()))
          .toList()
      : List.<ShowRuleToolResponse.Impact>of();
    
    var sections = (rule.descriptionSections() != null && !rule.descriptionSections().isEmpty())
      ? rule.descriptionSections().stream()
          .map(s -> new ShowRuleToolResponse.DescriptionSection(s.content()))
          .toList()
      : List.<ShowRuleToolResponse.DescriptionSection>of();
    
    return new ShowRuleToolResponse(
      rule.key(), rule.name(), rule.severity(), rule.type(),
      rule.lang(), rule.langName(), rule.htmlDesc(),
      impacts, sections
    );
  }

}
