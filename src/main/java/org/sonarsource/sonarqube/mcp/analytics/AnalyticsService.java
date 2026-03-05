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

import java.util.UUID;
import javax.annotation.Nullable;

public class AnalyticsService {

  private static final String CONNECTION_TYPE_SQC = "SQC";
  private static final String CONNECTION_TYPE_SQS = "SQS";

  private final AnalyticsClient client;
  private final String mcpServerId;
  private final String transportMode;
  private final boolean isSonarCloud;
  @Nullable
  private final String containerArch;

  public AnalyticsService(AnalyticsClient client, String mcpServerId, boolean isHttpEnabled, boolean isHttpsEnabled, boolean isSonarCloud) {
    this.client = client;
    this.mcpServerId = mcpServerId;
    this.transportMode = resolveTransportMode(isHttpEnabled, isHttpsEnabled);
    this.isSonarCloud = isSonarCloud;
    this.containerArch = resolveContainerArch();
  }

  private static String resolveTransportMode(boolean isHttpEnabled, boolean isHttpsEnabled) {
    if (!isHttpEnabled) {
      return "stdio";
    }
    return isHttpsEnabled ? "https" : "http";
  }

  @Nullable
  private static String resolveContainerArch() {
    var arch = System.getProperty("os.arch");
    if (arch == null) {
      return null;
    }
    return switch (arch) {
      case "amd64", "x86_64" -> "amd64";
      case "aarch64", "arm64" -> "arm64";
      default -> null;
    };
  }

  /**
   * Sends a McpToolInvoked event asynchronously. Errors are silently swallowed.
   *
   * @param toolName                 the MCP tool name that was invoked
   * @param organizationUuidV4       the SQC organisation UUID (null for SQS connections)
   * @param sqsInstallationId        the SQS installation identifier (null for SQC connections)
   * @param userUuid                 the user UUID (may be null when not available)
   * @param callingAgentName         the MCP client name (null in HTTP/S mode)
   * @param callingAgentVersion      the MCP client version (null in HTTP/S mode)
   * @param toolExecutionDurationMs  duration of the tool execution in milliseconds
   * @param isSuccessful             whether the tool execution completed without error
   * @param errorType                error category when unsuccessful, null on success
   * @param responseSizeBytes        byte size of the tool response content
   * @param invocationTimestamp      epoch milliseconds when the tool invocation started
   */
  public void notifyToolInvoked(String toolName, @Nullable String organizationUuidV4, @Nullable String sqsInstallationId, @Nullable String userUuid,
    @Nullable String callingAgentName, @Nullable String callingAgentVersion, long toolExecutionDurationMs, boolean isSuccessful,
    @Nullable String errorType, long responseSizeBytes, long invocationTimestamp) {
    var connectionType = isSonarCloud ? CONNECTION_TYPE_SQC : CONNECTION_TYPE_SQS;

    var event = new McpToolInvokedEvent(
      UUID.randomUUID().toString(),
      toolName,
      connectionType,
      isSonarCloud ? organizationUuidV4 : null,
      isSonarCloud ? null : sqsInstallationId,
      userUuid,
      mcpServerId,
      transportMode,
      callingAgentName,
      callingAgentVersion,
      toolExecutionDurationMs,
      isSuccessful,
      errorType,
      responseSizeBytes,
      containerArch,
      invocationTimestamp
    );

    client.postEvent(event);
  }

}
