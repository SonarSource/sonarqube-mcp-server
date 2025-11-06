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

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.InfoResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.SchemaUtils;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemInfoTool extends Tool {

  public static final String TOOL_NAME = "get_system_info";

  private final ServerApiProvider serverApiProvider;

  public SystemInfoTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SystemInfoToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube System Information")
      .setDescription("Get detailed information about SonarQube Server system configuration including JVM state, database, search indexes, and settings. " +
        "Requires 'Administer' permissions.")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApiProvider.get().systemApi().getInfo();
    var textResponse = buildResponseFromInfo(response);
    var structuredContent = buildStructuredContent(response);
    return Tool.Result.success(textResponse, structuredContent);
  }

  private static Map<String, Object> buildStructuredContent(InfoResponse response) {
    var sections = new ArrayList<SystemInfoToolResponse.Section>();
    
    addSection(sections, "System", response.system());
    addSection(sections, "Database", response.database());
    addSection(sections, "Bundled Plugins", response.bundled());
    addSection(sections, "Installed Plugins", response.plugins());
    addSection(sections, "Web JVM State", response.webJvmState());
    addSection(sections, "Web Database Connection", response.webDatabaseConnection());
    addSection(sections, "Web Logging", response.webLogging());
    addSection(sections, "Compute Engine Tasks", response.computeEngineTasks());
    addSection(sections, "Compute Engine JVM State", response.computeEngineJvmState());
    addSection(sections, "Compute Engine Database Connection", response.computeEngineDatabaseConnection());
    addSection(sections, "Compute Engine Logging", response.computeEngineLogging());
    addSection(sections, "Search State", response.searchState());
    addSection(sections, "Search Indexes", response.searchIndexes());
    addSection(sections, "ALMs", response.alms());
    addSection(sections, "Server Push Connections", response.serverPushConnections());
    addSection(sections, "Settings", response.settings());

    var toolResponse = new SystemInfoToolResponse(sections);
    return SchemaUtils.toStructuredContent(toolResponse);
  }

  private static void addSection(ArrayList<SystemInfoToolResponse.Section> sections, String name, @Nullable Map<String, Object> attributes) {
    if (attributes != null && !attributes.isEmpty()) {
      sections.add(new SystemInfoToolResponse.Section(name, attributes));
    }
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
