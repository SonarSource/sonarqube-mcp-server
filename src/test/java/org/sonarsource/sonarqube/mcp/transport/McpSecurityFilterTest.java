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
package org.sonarsource.sonarqube.mcp.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSecurityFilterTest {

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
  void should_reject_external_origin_when_bound_to_localhost() throws Exception {
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("https://malicious-site.com");
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(response).setContentType("application/json");
    var body = responseWriter.toString();
    assertThat(body)
      .contains("\"jsonrpc\":\"2.0\"")
      .contains("\"id\":null")
      .contains("\"code\":-32000")
      .contains("Origin not allowed");
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void should_reject_dns_rebinding_attack() throws Exception {
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("http://attacker-controlled-domain.com");
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void should_reject_subdomain_bypass_attack_localhost() throws Exception {
    // SECURITY: localhost.evil.com should NOT be allowed just because it starts with "localhost"
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("http://localhost.evil.com");
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    var body = responseWriter.toString();
    assertThat(body).contains("\"jsonrpc\":\"2.0\"").contains("Origin not allowed");
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void should_reject_subdomain_bypass_attack_127() throws Exception {
    // SECURITY: 127.0.0.1.evil.com should NOT be allowed
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("http://127.0.0.1.evil.com");
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @ParameterizedTest(name = "[{index}] hostBinding={0}, origin={1}")
  @MethodSource("allowedOriginScenarios")
  void should_accept_allowed_origins(String hostBinding, String origin, String method) throws Exception {
    var filter = new McpSecurityFilter(hostBinding);
    when(request.getHeader("Origin")).thenReturn(origin);
    when(request.getMethod()).thenReturn(method);

    filter.doFilter(request, response, filterChain);

    verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(filterChain).doFilter(request, response);
  }

  static Stream<Arguments> allowedOriginScenarios() {
    return Stream.of(
      Arguments.of("127.0.0.1", "http://localhost:3000", "POST"),
      Arguments.of("127.0.0.1", "http://127.0.0.1:8080", "POST"),
      Arguments.of("127.0.0.1", "https://localhost:3000", "POST"),
      Arguments.of("127.0.0.1", "http://[::1]:8080", "POST"),   // IPv6 localhost
      Arguments.of("127.0.0.1", "https://127.0.0.1:3000", "POST"),
      Arguments.of("127.0.0.1", "https://[::1]:8080", "POST"),  // IPv6 localhost over HTTPS
      Arguments.of("0.0.0.0", "https://external-site.com", "POST")
    );
  }

  @Test
  void should_use_wildcard_for_options_request_without_origin() throws Exception {
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn(null);
    when(request.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(request, response, filterChain);

    verify(response).setHeader("Access-Control-Allow-Origin", "*");
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void should_use_wildcard_when_bound_to_all_interfaces_without_origin() throws Exception {
    var filter = new McpSecurityFilter("0.0.0.0");
    when(request.getHeader("Origin")).thenReturn(null);
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setHeader("Access-Control-Allow-Origin", "*");
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void should_not_set_origin_header_when_no_origin_and_localhost_binding() throws Exception {
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn(null);
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response, never()).setHeader(eq("Access-Control-Allow-Origin"), any());
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void should_always_set_standard_cors_headers() throws Exception {
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("http://localhost:3000");
    when(request.getMethod()).thenReturn("POST");

    filter.doFilter(request, response, filterChain);

    verify(response).setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    verify(response).setHeader("Access-Control-Allow-Headers",
      "Content-Type, Accept, SONARQUBE_TOKEN, SONARQUBE_ORG, MCP-Protocol-Version");
    verify(response).setHeader("Access-Control-Max-Age", "3600");
  }


  @Test
  void should_handle_options_preflight_and_terminate() throws Exception {
    // Given: OPTIONS preflight request
    var filter = new McpSecurityFilter("127.0.0.1");
    when(request.getHeader("Origin")).thenReturn("http://localhost:3000");
    when(request.getMethod()).thenReturn("OPTIONS");

    // When
    filter.doFilter(request, response, filterChain);

    // Then: Should return 200 and not continue filter chain
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @ParameterizedTest(name = "[{index}] {4}")
  @MethodSource("edgeCaseScenarios")
  void should_handle_edge_cases(String hostBinding, String origin, String method, boolean shouldAccept, String description) throws Exception {
    var filter = new McpSecurityFilter(hostBinding);
    when(request.getHeader("Origin")).thenReturn(origin);
    when(request.getMethod()).thenReturn(method);

    filter.doFilter(request, response, filterChain);

    if (shouldAccept) {
      verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
      verify(filterChain).doFilter(request, response);
    } else {
      verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
      verify(filterChain, never()).doFilter(any(), any());
    }
  }

  static Stream<Arguments> edgeCaseScenarios() {
    return Stream.of(
      Arguments.of("127.0.0.1", "", "POST", false, "empty origin header should be rejected"),
      Arguments.of("localhost", "http://localhost:3000", "POST", true, "localhost binding accepts localhost origins"),
      Arguments.of("192.168.1.100", "http://localhost:3000", "POST", false, "custom host binding rejects all origins"),
      Arguments.of("127.0.0.1", "https://malicious.com", "GET", false, "GET with disallowed origin should be rejected"),
      Arguments.of("127.0.0.1", "http://localhost:3000", "POST", true, "POST with allowed origin should be accepted")
    );
  }

}

