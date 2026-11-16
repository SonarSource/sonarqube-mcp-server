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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Set;
import org.sonarsource.sonarqube.mcp.client.TransportMode;

import static org.sonarsource.sonarqube.mcp.client.TransportMode.HTTP;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.STDIO;

/** Central declarations of tool categories and availability requirements. */
public enum ToolDescriptor {
  GET_AGENTIC_READINESS_ASSESSMENT(ToolAvailability.AGENTIC_READINESS, Set.of(STDIO, HTTP), ToolCategory.AGENTIC_READINESS),
  LIST_AGENTIC_READINESS_ASSESSMENTS(ToolAvailability.AGENTIC_READINESS, Set.of(STDIO, HTTP), ToolCategory.AGENTIC_READINESS),
  START_AGENTIC_READINESS_ASSESSMENT(ToolAvailability.AGENTIC_READINESS, Set.of(STDIO, HTTP), ToolCategory.AGENTIC_READINESS),
  ANALYZE_CODE_SNIPPET(ToolAvailability.LOCAL_ANALYSIS, Set.of(STDIO, HTTP), ToolCategory.ANALYSIS),
  ANALYZE_FILE_LIST(ToolAvailability.IDE_ANALYSIS, Set.of(STDIO), ToolCategory.ANALYSIS, ToolCategory.IDE),
  RUN_ADVANCED_CODE_ANALYSIS(ToolAvailability.VORTEX_ANALYSIS, Set.of(STDIO), ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX),
  TOGGLE_AUTOMATIC_ANALYSIS(ToolAvailability.IDE_ANALYSIS, Set.of(STDIO), ToolCategory.ANALYSIS, ToolCategory.IDE),
  ARCHITECTURE_GET(ToolAvailability.ARCHITECTURE, Set.of(STDIO, HTTP), ToolCategory.ARCHITECTURE),
  ARCHITECTURE_SET(ToolAvailability.ARCHITECTURE, Set.of(STDIO, HTTP), ToolCategory.ARCHITECTURE),
  LIST_BRANCHES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.PROJECTS),
  SEARCH_DEPENDENCY_RISKS(ToolAvailability.DEPENDENCY_RISKS, Set.of(STDIO, HTTP), ToolCategory.DEPENDENCY_RISKS),
  GET_DUPLICATIONS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.DUPLICATIONS),
  SEARCH_DUPLICATED_FILES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.DUPLICATIONS),
  LIST_ENTERPRISES(ToolAvailability.CLOUD_ONLY, Set.of(STDIO, HTTP), ToolCategory.PORTFOLIOS),
  CHANGE_SECURITY_HOTSPOT_STATUS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.SECURITY_HOTSPOTS),
  SEARCH_SECURITY_HOTSPOTS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.SECURITY_HOTSPOTS),
  SHOW_SECURITY_HOTSPOT(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.SECURITY_HOTSPOTS),
  CHANGE_ISSUE_STATUS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.ISSUES),
  SEARCH_ISSUES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.ISSUES),
  LIST_LANGUAGES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.LANGUAGES),
  GET_COMPONENT_MEASURES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.MEASURES),
  SEARCH_FILES_BY_COVERAGE(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.COVERAGE),
  SEARCH_METRICS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.MEASURES),
  LIST_ORGANIZATIONS(ToolAvailability.CLOUD_ONLY, Set.of(STDIO, HTTP), ToolCategory.PROJECTS),
  LIST_PORTFOLIOS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.PORTFOLIOS),
  SEARCH_MY_PROJECTS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.PROJECTS),
  VORTEX_PROXY(ToolAvailability.VORTEX_PROXY, Set.of(STDIO), ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX),
  LIST_PULL_REQUESTS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.PROJECTS),
  LIST_QUALITY_GATES(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.QUALITY_GATES),
  PROJECT_STATUS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.QUALITY_GATES),
  SHOW_RULE(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.RULES),
  GET_FILE_COVERAGE_DETAILS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.COVERAGE),
  GET_RAW_SOURCE(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.SOURCES),
  GET_SCM_INFO(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.SOURCES),
  SYSTEM_HEALTH(ToolAvailability.SERVER_ONLY, Set.of(STDIO, HTTP), ToolCategory.SYSTEM),
  SYSTEM_INFO(ToolAvailability.SERVER_ONLY, Set.of(STDIO, HTTP), ToolCategory.SYSTEM),
  SYSTEM_LOGS(ToolAvailability.SERVER_ONLY, Set.of(STDIO, HTTP), ToolCategory.SYSTEM),
  SYSTEM_PING(ToolAvailability.SERVER_ONLY, Set.of(STDIO, HTTP), ToolCategory.SYSTEM),
  SYSTEM_STATUS(ToolAvailability.SERVER_ONLY, Set.of(STDIO, HTTP), ToolCategory.SYSTEM),
  CREATE_WEBHOOK(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.WEBHOOKS),
  LIST_WEBHOOKS(ToolAvailability.COMMON, Set.of(STDIO, HTTP), ToolCategory.WEBHOOKS);

  private final Set<ToolCategory> categories;
  private final Set<TransportMode> transports;
  private final ToolAvailability availability;

  ToolDescriptor(ToolAvailability availability, Set<TransportMode> transports, ToolCategory... categories) {
    this.categories = Set.of(categories);
    this.transports = Set.copyOf(transports);
    this.availability = availability;
  }

  public Set<ToolCategory> getCategories() {
    return categories;
  }

  public Set<TransportMode> getTransports() {
    return transports;
  }

  public ToolAvailability getAvailability() {
    return availability;
  }
}
