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
package org.sonarsource.sonarqube.mcp.serverapi.system.response;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public record InfoResponse(
  @Nullable @SerializedName("Health") String health,
  @Nullable @SerializedName("Health Causes") List<String> healthCauses,
  @Nullable @SerializedName("System") Map<String, Object> system,
  @Nullable @SerializedName("Database") Map<String, Object> database,
  @Nullable @SerializedName("Bundled") Map<String, Object> bundled,
  @Nullable @SerializedName("Plugins") Map<String, Object> plugins,
  @Nullable @SerializedName("Web JVM State") Map<String, Object> webJvmState,
  @Nullable @SerializedName("Web Database Connection") Map<String, Object> webDatabaseConnection,
  @Nullable @SerializedName("Web Logging") Map<String, Object> webLogging,
  @Nullable @SerializedName("Web JVM Properties") Map<String, Object> webJvmProperties,
  @Nullable @SerializedName("Compute Engine Tasks") Map<String, Object> computeEngineTasks,
  @Nullable @SerializedName("Compute Engine JVM State") Map<String, Object> computeEngineJvmState,
  @Nullable @SerializedName("Compute Engine Database Connection") Map<String, Object> computeEngineDatabaseConnection,
  @Nullable @SerializedName("Compute Engine Logging") Map<String, Object> computeEngineLogging,
  @Nullable @SerializedName("Compute Engine JVM Properties") Map<String, Object> computeEngineJvmProperties,
  @Nullable @SerializedName("Search State") Map<String, Object> searchState,
  @Nullable @SerializedName("Search Indexes") Map<String, Object> searchIndexes,
  @Nullable @SerializedName("ALMs") Map<String, Object> alms,
  @Nullable @SerializedName("Server Push Connections") Map<String, Object> serverPushConnections,
  @Nullable @SerializedName("Settings") Map<String, Object> settings
) {
}
