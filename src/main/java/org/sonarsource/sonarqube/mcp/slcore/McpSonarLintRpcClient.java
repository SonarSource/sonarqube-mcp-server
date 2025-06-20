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
package org.sonarsource.sonarqube.mcp.slcore;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.ConnectionNotFoundException;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintRpcClientDelegate;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.NoBindingSuggestionFoundParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.GetProxyPasswordAuthenticationResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.ProxyDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.http.X509CertificateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.TelemetryClientLiveAttributesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

public class McpSonarLintRpcClient implements SonarLintRpcClientDelegate {

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    //
  }

  @Override
  public void suggestConnection(Map<String, List<ConnectionSuggestionDto>> suggestionsByConfigScope) {
    //
  }

  @Override
  public void openUrlInBrowser(URL url) {
    //
  }

  @Override
  public void showMessage(MessageType type, String text) {
    //
  }

  @Override
  public void log(LogParams params) {
    // never called, logs will not come from this method but directly appended to the log file
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {
    //
  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams params) {
    //
  }

  @Override
  public String getClientLiveDescription() {
    return "";
  }

  @Override
  public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {
    //
  }

  @Override
  public void showIssue(String configurationScopeId, IssueDetailsDto issueDetails) {
    //
  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, SonarLintCancelChecker cancelChecker) throws CancellationException {
    return null;
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, SonarLintCancelChecker cancelChecker) throws CancellationException {
    return null;
  }

  @Override
  public void startProgress(StartProgressParams params) throws UnsupportedOperationException {
    //
  }

  @Override
  public void reportProgress(ReportProgressParams params) {
    //
  }

  @Override
  public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
    //
  }

  @Override
  public @Nullable Either<TokenDto, UsernamePasswordDto> getCredentials(String connectionId) throws ConnectionNotFoundException {
    return null;
  }

  @Override
  public List<ProxyDto> selectProxies(URI uri) {
    return List.of();
  }

  @Override
  public GetProxyPasswordAuthenticationResponse getProxyPasswordAuthentication(String host, int port, String protocol, String prompt, String scheme, URL targetHost) {
    return null;
  }

  @Override
  public boolean checkServerTrusted(List<X509CertificateDto> chain, String authType) {
    return false;
  }

  @Override
  public @Nullable String matchSonarProjectBranch(String configurationScopeId, String mainBranchName,
    Set<String> allBranchesNames, SonarLintCancelChecker cancelChecker) {
    return null;
  }

  @Override
  public boolean matchProjectBranch(String configurationScopeId, String branchNameToMatch, SonarLintCancelChecker cancelChecker) throws ConfigScopeNotFoundException {
    return false;
  }

  @Override
  public void didChangeMatchedSonarProjectBranch(String configScopeId, String newMatchedBranchName) {
    //
  }

  @Override
  public TelemetryClientLiveAttributesResponse getTelemetryLiveAttributes() {
    return new TelemetryClientLiveAttributesResponse(Map.of());
  }

  @Override
  public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
    List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
    //
  }

  @Override
  public List<ClientFileDto> listFiles(String configScopeId) {
    return List.of();
  }

  @Override
  public void noBindingSuggestionFound(NoBindingSuggestionFoundParams params) {
    //
  }

  @Override
  public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    //
  }

}
