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
package org.sonarsource.sonarqube.mcp.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Thread-safe store for session-to-token mappings with TTL-based expiration.
 * Sessions are automatically expired after a period of inactivity to prevent
 * memory leaks and ensure stale sessions are cleaned up.
 * <p>
 * Flow:
 * <ol>
 *   <li>Filter extracts session ID and token from HTTP request</li>
 *   <li>Filter stores/validates the mapping: session_id → token</li>
 *   <li>Tool execution gets session ID from MCP exchange.sessionId()</li>
 *   <li>Tool looks up the correct token from this store</li>
 * </ol>
 */
public class SessionTokenStore {

  private static final Duration SESSION_TTL = Duration.ofHours(1);
  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);
  private static final SessionTokenStore INSTANCE = new SessionTokenStore();

  /**
   * Session entry with token and last access timestamp
   */
  private record SessionEntry(String token, Instant lastAccess) {
    SessionEntry touch() {
      return new SessionEntry(token, Instant.now());
    }
  }

  // Maps sessionId → SessionEntry (token + last access time)
  private final ConcurrentHashMap<String, SessionEntry> sessionTokens = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler;

  private SessionTokenStore() {
    cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      var thread = new Thread(r, "session-cleanup");
      thread.setDaemon(true);
      return thread;
    });
    cleanupScheduler.scheduleAtFixedRate(
      this::cleanupExpiredSessions,
      CLEANUP_INTERVAL.toMinutes(),
      CLEANUP_INTERVAL.toMinutes(),
      TimeUnit.MINUTES
    );
  }

  public static SessionTokenStore getInstance() {
    return INSTANCE;
  }

  /**
   * Store the token for a NEW session only.
   * For existing sessions, validates that the token matches and refreshes the TTL.
   */
  public boolean setTokenIfValid(String sessionId, String token) {
    var newEntry = new SessionEntry(token, Instant.now());
    var result = new AtomicBoolean(true);
    sessionTokens.compute(sessionId, (key, existing) -> {
      if (existing == null) {
        // New session - store the entry
        return newEntry;
      }
      // Existing session - validate token matches
      if (existing.token().equals(token)) {
        // Token matches - refresh TTL
        return existing.touch();
      }
      // Token mismatch
      result.set(false);
      return existing;
    });

    return result.get();
  }

  /**
   * Get the token for a session, refreshing its TTL.
   * Called by tool execution to get the correct token.
   */
  @Nullable
  public String getToken(String sessionId) {
    var entry = sessionTokens.computeIfPresent(sessionId, (key, existing) -> {
      // Check if expired
      if (isExpired(existing)) {
        return null;
      }
      // Refresh TTL on access
      return existing.touch();
    });

    return entry != null ? entry.token() : null;
  }

  /**
   * Get the number of active sessions. Useful for monitoring.
   */
  public int size() {
    return sessionTokens.size();
  }

  public void clear() {
    sessionTokens.clear();
  }

  /**
   * Shutdown the cleanup scheduler. Should be called on server shutdown.
   */
  public void shutdown() {
    cleanupScheduler.shutdown();
    try {
      if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    clear();
  }

  private static boolean isExpired(SessionEntry entry) {
    return Duration.between(entry.lastAccess(), Instant.now()).compareTo(SESSION_TTL) > 0;
  }

  private void cleanupExpiredSessions() {
    sessionTokens.entrySet().removeIf(entry -> isExpired(entry.getValue()));
  }

}
