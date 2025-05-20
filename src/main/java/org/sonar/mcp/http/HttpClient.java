/*
 * Sonar MCP Server
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
package org.sonar.mcp.http;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface HttpClient {

  interface Response extends Closeable {

    int code();

    default boolean isSuccessful() {
      return code() >= 200 && code() < 300;
    }

    String bodyAsString();

    /**
     * Only runtime exception
     */
    @Override
    void close();

    String url();
  }

  Response get(String url);

  CompletableFuture<Response> getAsync(String url);

  Response post(String url, String contentType, String body);

  CompletableFuture<Response> postAsync(String url, String contentType, String body);

}
