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
package org.sonarsource.sonarqube.mcp.serverapi;

import jakarta.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Provider interface for obtaining ServerApi instances.
 * This abstraction allows tools to work in both stdio and HTTP modes:
 * - In stdio mode: Returns the global ServerApi instance created at startup
 * - In HTTP mode: Creates per-request ServerApi instances using the client token from the Authorization: Bearer header
 */
public interface ServerApiProvider extends Supplier<ServerApi> {

  /**
   * Returns a ServerApi for the current request, optionally overriding the organization key.
   * When {@code orgOverride} is non-null and non-blank, the returned ServerApi is bound to that
   * organization instead of the one configured/resolved at startup (or provided per-request via
   * HTTP header). Implementations should fall back to {@link #get()} when no override applies.
   */
  default ServerApi get(@Nullable String orgOverride) {
    return get();
  }
}

