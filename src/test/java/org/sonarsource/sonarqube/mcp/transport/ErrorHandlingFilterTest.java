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
package org.sonarsource.sonarqube.mcp.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorHandlingFilterTest {

  private final ErrorHandlingFilter filter = new ErrorHandlingFilter();
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
  void should_pass_through_when_no_exception() throws Exception {
    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void should_catch_runtime_exception_and_return_json_rpc_error() throws Exception {
    doThrow(new RuntimeException("something broke")).when(filterChain).doFilter(any(), any());

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    verify(response).setContentType("application/json");
    verify(response).setCharacterEncoding("UTF-8");
    var body = responseWriter.toString();
    assertThat(body)
      .contains("\"jsonrpc\":\"2.0\"")
      .contains("\"id\":null")
      .contains("\"code\":-32603")
      .contains("Internal error");
  }

  @Test
  void should_catch_servlet_exception_and_return_json_rpc_error() throws Exception {
    doThrow(new ServletException("servlet failure")).when(filterChain).doFilter(any(), any());

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    var body = responseWriter.toString();
    assertThat(body).contains("\"code\":-32603");
  }

  @Test
  void should_catch_io_exception_and_return_json_rpc_error() throws Exception {
    doThrow(new IOException("connection reset")).when(filterChain).doFilter(any(), any());

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    var body = responseWriter.toString();
    assertThat(body).contains("\"code\":-32603");
  }

  @Test
  void should_not_write_response_when_already_committed() throws Exception {
    when(response.isCommitted()).thenReturn(true);
    doThrow(new RuntimeException("late failure")).when(filterChain).doFilter(any(), any());

    filter.doFilter(request, response, filterChain);

    verify(response, never()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    assertThat(responseWriter.toString()).isEmpty();
  }

}
