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
public record McpClientInitializedEvent(
  @JsonProperty("mcp_server_id") String mcpServerId,
  @JsonProperty("mcp_server_version") String mcpServerVersion,
  @JsonProperty("connection_type") String connectionType,
  @JsonProperty("transport_mode") String transportMode,
  @JsonProperty("authentication_method") String authenticationMethod,
  @JsonProperty("calling_agent_name") @Nullable String callingAgentName,
  @JsonProperty("calling_agent_version") @Nullable String callingAgentVersion,
  @JsonProperty("initialization_timestamp") long initializationTimestamp,
  @JsonProperty("oauth_client_key") @Nullable String oauthClientKey,
  @JsonProperty("user_uuid") @Nullable String userUuid,
  @JsonProperty("sqs_installation_id") @Nullable String sqsInstallationId
) implements AnalyticsEvent {
  @Override
  @JsonIgnore
  public String eventType() {
    return "Analytics.Mcp.McpClientInitialized";
  }

  @Override
  @JsonIgnore
  public String eventVersion() {
    return "1";
  }
}
