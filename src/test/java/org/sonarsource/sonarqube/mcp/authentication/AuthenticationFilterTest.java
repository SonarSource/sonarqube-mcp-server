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
package org.sonarsource.sonarqube.mcp.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain filterChain;
  private StringWriter responseWriter;

  @BeforeEach
  void setUp() throws Exception {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    filterChain = mock(FilterChain.class);
    responseWriter = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
  }

  @Test
  void should_always_allow_options_requests() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void should_allow_request_with_sonarqube_token_header() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("squ_my_custom_token");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(request).setAttribute(AuthenticationFilter.SONARQUBE_TOKEN_ATTRIBUTE, "squ_my_custom_token");
  }

  @Test
  void should_trim_token_with_extra_whitespace() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("  squ_token123  ");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(request).setAttribute(AuthenticationFilter.SONARQUBE_TOKEN_ATTRIBUTE, "squ_token123");
  }

  @Test
  void should_reject_request_without_token_header() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    verify(filterChain, never()).doFilter(request, response);
    var responseJson = responseWriter.toString();
    assertThat(responseJson)
      .contains("\"error\":\"unauthorized\"")
      .contains("SonarQube token required");
  }

  @Test
  void should_reject_request_with_empty_token() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("");
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void should_include_www_authenticate_header_in_401_response() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");

    filter.doFilter(request, response, filterChain);

    verify(response).setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    verify(response).setContentType("application/json");
  }

  @Test
  void should_return_json_error_response() throws Exception {
    var filter = new AuthenticationFilter(AuthMode.TOKEN);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");

    filter.doFilter(request, response, filterChain);

    var responseJson = responseWriter.toString();
    assertThat(responseJson)
      .contains("\"error\":\"unauthorized\"")
      .contains("\"error_description\":");
  }

}


