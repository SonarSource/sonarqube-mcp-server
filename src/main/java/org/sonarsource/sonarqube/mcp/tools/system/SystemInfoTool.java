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
package org.sonarsource.sonarqube.mcp.tools.system;

import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.InfoResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemInfoTool extends Tool {

  public static final String TOOL_NAME = "get_system_info";

  private final ServerApi serverApi;

  public SystemInfoTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get detailed information about system configuration including JVM state, database, search indexes, and settings. Requires 'Administer' permissions.")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Server, please provide valid credentials");
    }

    var response = serverApi.systemApi().getInfo();
    return Tool.Result.success(buildResponseFromInfo(response));
  }

  private static String buildResponseFromInfo(InfoResponse response) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append("SonarQube Server System Information\n");
    stringBuilder.append("===========================\n\n");

    if (response.health() != null) {
      stringBuilder.append("Health: ").append(response.health()).append("\n\n");
    }

    appendSection(stringBuilder, "System", response.system());
    appendSection(stringBuilder, "Database", response.database());
    appendSection(stringBuilder, "Bundled Plugins", response.bundled());
    appendSection(stringBuilder, "Installed Plugins", response.plugins());
    appendSection(stringBuilder, "Web JVM State", response.webJvmState());
    appendSection(stringBuilder, "Web Database Connection", response.webDatabaseConnection());
    appendSection(stringBuilder, "Web Logging", response.webLogging());
    appendSection(stringBuilder, "Compute Engine Tasks", response.computeEngineTasks());
    appendSection(stringBuilder, "Compute Engine JVM State", response.computeEngineJvmState());
    appendSection(stringBuilder, "Compute Engine Database Connection", response.computeEngineDatabaseConnection());
    appendSection(stringBuilder, "Compute Engine Logging", response.computeEngineLogging());
    appendSection(stringBuilder, "Search State", response.searchState());
    appendSection(stringBuilder, "Search Indexes", response.searchIndexes());
    appendSection(stringBuilder, "ALMs", response.alms());
    appendSection(stringBuilder, "Server Push Connections", response.serverPushConnections());

    // The Settings section is typically very large, so we'll show a summary
    if (response.settings() != null && !response.settings().isEmpty()) {
      stringBuilder.append("Settings\n");
      stringBuilder.append("--------\n");
      stringBuilder.append("Total settings: ").append(response.settings().size()).append("\n");
      stringBuilder.append("(Use SonarQube Server Web UI to view detailed settings)\n\n");
    }

    return stringBuilder.toString().trim();
  }

  private static void appendSection(StringBuilder stringBuilder, String sectionName, @Nullable Map<String, Object> section) {
    if (section != null && !section.isEmpty()) {
      stringBuilder.append(sectionName).append("\n");
      stringBuilder.append("-".repeat(sectionName.length())).append("\n");
      for (Map.Entry<String, Object> entry : section.entrySet()) {
        stringBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
      }
      stringBuilder.append("\n");
    }
  }
}
