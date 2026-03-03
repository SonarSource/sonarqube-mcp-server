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
package org.sonarsource.sonarqube.mcp.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolInvokedEvent(
  @JsonProperty("invocation_id") String invocationId,
  @JsonProperty("tool_name") String toolName,
  @JsonProperty("connection_type") String connectionType,
  @JsonProperty("organization_uuid_v4") @Nullable String organizationUuidV4,
  @JsonProperty("sqs_installation_id") @Nullable String sqsInstallationId,
  @JsonProperty("user_uuid") @Nullable String userUuid,
  @JsonProperty("mcp_server_id") String mcpServerId,
  @JsonProperty("transport_mode") String transportMode,
  @JsonProperty("calling_agent_name") @Nullable String callingAgentName,
  @JsonProperty("calling_agent_version") @Nullable String callingAgentVersion,
  @JsonProperty("tool_execution_duration_ms") long toolExecutionDurationMs,
  @JsonProperty("is_successful") boolean isSuccessful
) implements AnalyticsEvent {

  private static final String EVENT_TYPE = "Analytics.Mcp.McpToolInvoked";
  private static final String EVENT_VERSION = "0";

  @Override
  @JsonIgnore
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  @JsonIgnore
  public String eventVersion() {
    return EVENT_VERSION;
  }

}
