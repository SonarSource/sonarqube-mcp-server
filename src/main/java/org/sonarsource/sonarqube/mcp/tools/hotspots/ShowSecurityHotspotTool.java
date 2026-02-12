/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.hotspots;

import java.util.Collections;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.response.ShowResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ShowSecurityHotspotTool extends Tool {

  public static final String TOOL_NAME = "show_security_hotspot";
  public static final String HOTSPOT_KEY_PROPERTY = "hotspotKey";

  private final ServerApiProvider serverApiProvider;

  public ShowSecurityHotspotTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ShowSecurityHotspotToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Show Security Hotspot Details")
      .setDescription("Get detailed information about a specific Security Hotspot, including rule details, code context, flows, and comments.")
      .addRequiredStringProperty(HOTSPOT_KEY_PROPERTY, "The key of the Security Hotspot to retrieve")
      .setReadOnlyHint()
      .build(),
      ToolCategory.SECURITY_HOTSPOTS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var hotspotKey = arguments.getStringOrThrow(HOTSPOT_KEY_PROPERTY);
    var response = serverApiProvider.get().hotspotsApi().show(hotspotKey);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static ShowSecurityHotspotToolResponse buildStructuredContent(ShowResponse response) {
    ShowSecurityHotspotToolResponse.TextRange textRange = null;
    if (response.textRange() != null) {
      textRange = new ShowSecurityHotspotToolResponse.TextRange(
        response.textRange().startLine(),
        response.textRange().endLine(),
        response.textRange().startOffset(),
        response.textRange().endOffset()
      );
    }

    List<ShowSecurityHotspotToolResponse.Flow> flows = response.flows() != null ? response.flows().stream()
      .map(flow -> {
        var locations = flow.locations().stream()
          .map(loc -> new ShowSecurityHotspotToolResponse.Location(
            loc.component(),
            new ShowSecurityHotspotToolResponse.TextRange(
              loc.textRange().startLine(),
              loc.textRange().endLine(),
              loc.textRange().startOffset(),
              loc.textRange().endOffset()
            ),
            loc.msg()
          ))
          .toList();
        return new ShowSecurityHotspotToolResponse.Flow(locations);
      })
      .toList() : Collections.emptyList();

    List<ShowSecurityHotspotToolResponse.Comment> comments = response.comments() != null ? response.comments().stream()
      .map(comment -> new ShowSecurityHotspotToolResponse.Comment(
        comment.key(),
        comment.login(),
        comment.htmlText(),
        comment.markdown(),
        comment.updatable(),
        comment.createdAt()
      ))
      .toList() : Collections.emptyList();

    var rule = new ShowSecurityHotspotToolResponse.Rule(
      response.rule().key(),
      response.rule().name(),
      response.rule().securityCategory(),
      response.rule().vulnerabilityProbability(),
      response.rule().riskDescription(),
      response.rule().vulnerabilityDescription(),
      response.rule().fixRecommendations()
    );

    return new ShowSecurityHotspotToolResponse(
      response.key(),
      response.component().key(),
      response.project().key(),
      response.securityCategory() != null ? response.securityCategory() : response.rule().securityCategory(),
      response.vulnerabilityProbability() != null ? response.vulnerabilityProbability() : response.rule().vulnerabilityProbability(),
      response.status(),
      response.resolution(),
      response.line(),
      response.message(),
      response.assignee(),
      response.author(),
      response.creationDate(),
      response.updateDate(),
      textRange,
      flows,
      comments,
      rule,
      response.canChangeStatus()
    );
  }

}
