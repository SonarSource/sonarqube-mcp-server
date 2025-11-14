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
package org.sonarsource.sonarqube.mcp.tools.system;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.InfoResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
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
      .setReadOnlyHint()
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApiProvider.get().systemApi().getInfo();
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static SystemInfoToolResponse buildStructuredContent(InfoResponse response) {
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

    return new SystemInfoToolResponse(sections);
  }

  private static void addSection(ArrayList<SystemInfoToolResponse.Section> sections, String name, @Nullable Map<String, Object> attributes) {
    if (attributes != null && !attributes.isEmpty()) {
      sections.add(new SystemInfoToolResponse.Section(name, attributes));
    }
  }

}
