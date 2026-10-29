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
package org.sonarsource.sonarqube.mcp.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpOAuthAuthenticationFinishedEvent(
  @JsonProperty("mcp_server_id") String mcpServerId,
  @JsonProperty("mcp_server_version") String mcpServerVersion,
  @JsonProperty("connection_type") String connectionType,
  @JsonProperty("transport_mode") String transportMode,
  @JsonProperty("authentication_timestamp") long authenticationTimestamp,
  @JsonProperty("outcome") String outcome,
  @JsonProperty("failure_stage") @Nullable String failureStage,
  @JsonProperty("failure_category") @Nullable String failureCategory,
  @JsonProperty("oauth_client_key") @Nullable String oauthClientKey
) implements AnalyticsEvent {
  @Override
  @JsonIgnore
  public String eventType() {
    return "Analytics.Mcp.McpOAuthAuthenticationFinished";
  }

  @Override
  @JsonIgnore
  public String eventVersion() {
    return "1";
  }
}
