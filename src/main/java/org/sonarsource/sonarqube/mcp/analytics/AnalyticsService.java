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


import jakarta.annotation.Nullable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;

public class AnalyticsService {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String CONNECTION_TYPE_SQC = "SQC";
  private static final String CONNECTION_TYPE_SQS = "SQS";

  private final AnalyticsClient client;
  private final String mcpServerId;
  private final String mcpServerVersion;
  private final String transportMode;
  private final boolean isSonarQubeCloud;
  @Nullable
  private final String containerArch;
  private final ExecutorService executor;

  public AnalyticsService(AnalyticsClient client, String mcpServerId, String mcpServerVersion, boolean isHttpEnabled, boolean isHttpsEnabled, boolean isSonarQubeCloud) {
    this.client = client;
    this.mcpServerId = mcpServerId;
    this.mcpServerVersion = mcpServerVersion;
    this.transportMode = resolveTransportMode(isHttpEnabled, isHttpsEnabled);
    this.isSonarQubeCloud = isSonarQubeCloud;
    this.containerArch = resolveContainerArch();
    this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000), r -> {
      var thread = new Thread(r, "analytics-dispatcher");
      thread.setDaemon(true);
      return thread;
    });
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
   */
  public void notifyToolInvoked(ToolInvocationResult result) {
    var connectionType = isSonarQubeCloud ? CONNECTION_TYPE_SQC : CONNECTION_TYPE_SQS;

    var event = new McpToolInvokedEvent(
      result.invocationId(),
      result.toolName(),
      connectionType,
      isSonarQubeCloud ? result.organizationUuidV4() : null,
      isSonarQubeCloud ? null : result.sqsInstallationId(),
      result.userUuid(),
      mcpServerId,
      mcpServerVersion,
      transportMode,
      result.callingAgentName(),
      result.callingAgentVersion(),
      result.toolExecutionDurationMs(),
      result.isSuccessful(),
      result.errorType(),
      result.responseSizeBytes(),
      containerArch,
      result.invocationTimestamp(),
      result.matchingToolsets()
    );

    client.postEvent(event);
  }

  public void notifyClientInitialized(HttpAnalyticsContext context, @Nullable String name, @Nullable String version, long timestamp,
    Supplier<ServerApi> apiSupplier) {
    submit(() -> {
      var identity = ConnectionContext.empty();
      try {
        var api = apiSupplier.get();
        if (api != null) {
          identity.resolveIdentityFrom(api);
        }
      } catch (RuntimeException e) {
        LOG.debug("Unable to enrich initialization analytics (" + e.getClass().getSimpleName() + ")");
      }
      client.postEvent(new McpClientInitializedEvent(mcpServerId, mcpServerVersion,
        isSonarQubeCloud ? CONNECTION_TYPE_SQC : CONNECTION_TYPE_SQS, transportMode,
        context.authenticationMethod(), name, version, timestamp, context.oauthClientKey(), identity.getUserUuid(), identity.getSqsInstallationId()));
    });
  }

  public void notifyOAuthAuthenticationCompleted(OAuthAuthenticationResult result) {
    submit(() -> client.postEvent(new McpOAuthAuthenticationCompletedEvent(mcpServerId, mcpServerVersion,
      isSonarQubeCloud ? CONNECTION_TYPE_SQC : CONNECTION_TYPE_SQS, transportMode,
      result.timestamp(), result.durationMs(), result.outcome(), result.failureStage(), result.failureCategory(), result.oauthClientKey())));
  }

  public void submit(Runnable task) {
    try {
      executor.submit(task);
    } catch (RuntimeException e) {
      // A full queue or shutdown must not change an MCP response.
      LOG.debug("Unable to schedule analytics (" + e.getClass().getSimpleName() + ")");
    }
  }

  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

}
