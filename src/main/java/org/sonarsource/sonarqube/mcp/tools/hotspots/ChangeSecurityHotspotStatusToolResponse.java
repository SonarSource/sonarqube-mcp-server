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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;

public record ChangeSecurityHotspotStatusToolResponse(
  @JsonPropertyDescription("Whether the operation was successful") boolean success,
  @JsonPropertyDescription("Success or error message") String message,
  @JsonPropertyDescription("The key of the Security Hotspot that was updated") String hotspotKey,
  @JsonPropertyDescription("The new status of the Security Hotspot") String newStatus,
  @Nullable @JsonPropertyDescription("The new resolution of the Security Hotspot (if status is REVIEWED)") String newResolution
) {}
