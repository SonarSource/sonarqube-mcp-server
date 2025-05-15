/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.sonarsource.sonarlint.core.http.HttpClient;

class HttpResponse implements HttpClient.Response {

  private final String requestUrl;
  private final SimpleHttpResponse response;

  public HttpResponse(String requestUrl, SimpleHttpResponse response) {
    this.requestUrl = requestUrl;
    this.response = response;
  }

  @Override
  public int code() {
    return response.getCode();
  }

  @Override
  public String bodyAsString() {
    return response.getBodyText();
  }

  @Override
  public InputStream bodyAsStream() {
    if (response.getBodyBytes() == null) {
      return new ByteArrayInputStream(new byte[0]);
    }
    return new ByteArrayInputStream(response.getBodyBytes());
  }

  @Override
  public void close() {
    // nothing to do
  }

  @Override
  public String url() {
    return requestUrl;
  }

  @Override
  public String toString() {
    return response.toString();
  }

}
