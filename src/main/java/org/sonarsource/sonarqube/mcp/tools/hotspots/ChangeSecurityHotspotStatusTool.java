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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ChangeSecurityHotspotStatusTool extends Tool {

  public static final String TOOL_NAME = "change_security_hotspot_status";
  public static final String HOTSPOT_KEY_PROPERTY = "hotspotKey";
  public static final String STATUS_PROPERTY = "status";
  public static final String RESOLUTION_PROPERTY = "resolution";
  public static final String COMMENT_PROPERTY = "comment";

  private final ServerApiProvider serverApiProvider;

  public ChangeSecurityHotspotStatusTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ChangeSecurityHotspotStatusToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Change Security Hotspot Status")
      .setDescription("""
        Change the status of a Security Hotspot to review it. When marking as REVIEWED, you must specify a resolution.
        - TO_REVIEW: Mark the Security Hotspot as needing review
        - REVIEWED: Mark the Security Hotspot as reviewed with one of these resolutions:
          * FIXED: A fix has been implemented
          * SAFE: Reviewed and determined to be safe
          * ACKNOWLEDGED: Acknowledged as a risk but accepted
        You can optionally add a comment to explain your review decision.""")
      .addRequiredStringProperty(HOTSPOT_KEY_PROPERTY, "The key of the Security Hotspot to update")
      .addRequiredEnumProperty(STATUS_PROPERTY, new String[] {"TO_REVIEW", "REVIEWED"}, "The new status of the Security Hotspot")
      .addStringProperty(RESOLUTION_PROPERTY, "The resolution when status is REVIEWED. Required if status is REVIEWED. Possible values: FIXED, SAFE, ACKNOWLEDGED")
      .addStringProperty(COMMENT_PROPERTY, "An optional comment explaining the review decision")
      .build(),
      ToolCategory.SECURITY_HOTSPOTS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var hotspotKey = arguments.getStringOrThrow(HOTSPOT_KEY_PROPERTY);
    var statusList = arguments.getStringListOrThrow(STATUS_PROPERTY);
    var status = statusList.getFirst();
    var resolution = arguments.getOptionalString(RESOLUTION_PROPERTY);
    var comment = arguments.getOptionalString(COMMENT_PROPERTY);

    // Validate that resolution is provided when status is REVIEWED
    if ("REVIEWED".equals(status) && (resolution == null || resolution.isEmpty())) {
      return Tool.Result.failure("Resolution is required when status is REVIEWED. Valid resolutions: FIXED, SAFE, ACKNOWLEDGED");
    }

    // Validate that resolution is not provided when status is TO_REVIEW
    if ("TO_REVIEW".equals(status) && resolution != null && !resolution.isEmpty()) {
      return Tool.Result.failure("Resolution should not be provided when status is TO_REVIEW");
    }

    serverApiProvider.get().hotspotsApi().changeStatus(hotspotKey, status, resolution, comment);
    
    var message = "The Security Hotspot status was successfully changed.";
    var response = new ChangeSecurityHotspotStatusToolResponse(true, message, hotspotKey, status, resolution);
    return Tool.Result.success(response);
  }

}
