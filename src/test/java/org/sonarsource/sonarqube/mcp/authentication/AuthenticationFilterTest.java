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
package org.sonarsource.sonarqube.mcp.authentication;

import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter.OAuthSupport;
import org.sonarsource.sonarqube.mcp.transport.OAuthScopeResponseWrapper;

import static java.util.Collections.enumeration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.READ;
import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.WRITE;

class AuthenticationFilterTest {

  private static final OAuthProtectedResourceMetadata OAUTH_METADATA =
    new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://login.example.com/");

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
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void should_allow_request_with_authorization_bearer_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_my_custom_token");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void should_allow_request_with_deprecated_sonarqube_token_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("squ_legacy_token");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void should_prefer_authorization_bearer_over_deprecated_sonarqube_token_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_new_token");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("squ_legacy_token");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void should_reject_request_without_authorization_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    verify(filterChain, never()).doFilter(request, response);
    var responseJson = responseWriter.toString();
    assertThat(responseJson)
      .contains("\"jsonrpc\":\"2.0\"")
      .contains("\"id\":null")
      .contains("\"code\":-32000")
      .contains("SonarQube token required");
  }

  @Test
  void should_reject_request_with_non_bearer_authorization_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void should_reject_request_with_empty_bearer_token() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer ");
    when(request.getRemoteAddr()).thenReturn("192.168.1.100");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(filterChain, never()).doFilter(request, response);
  }

  @Test
  void should_include_www_authenticate_header_in_401_response() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");

    filter.doFilter(request, response, filterChain);

    verify(response).setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    verify(response).setContentType("application/json");
  }

  @Test
  void should_return_jsonrpc_error_response_body() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");

    filter.doFilter(request, response, filterChain);

    var responseJson = responseWriter.toString();
    assertThat(responseJson)
      .contains("\"jsonrpc\":\"2.0\"")
      .contains("\"id\":null")
      .contains("\"error\":{")
      .contains("\"code\":-32000")
      .contains("\"message\":");
  }

  @ParameterizedTest(name = "[{index}] org={0}")
  @MethodSource("organizationHeaders")
  void should_allow_requests_with_optional_organization_header(String orgHeader) throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_token");
    when(request.getHeader("SONARQUBE_ORG")).thenReturn(orgHeader);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @ParameterizedTest(name = "[{index}] fixed-org header={0}")
  @MethodSource("organizationHeaders")
  void should_preserve_fixed_server_organization_header_rejection(String orgHeader) throws Exception {
    var filter = tokenFilter("my-org");
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_token");
    when(request.getHeader("SONARQUBE_ORG")).thenReturn(orgHeader);

    filter.doFilter(request, response, filterChain);

    if (orgHeader == null || orgHeader.isBlank()) {
      verify(filterChain).doFilter(request, response);
      verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    } else {
      verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
      verify(filterChain, never()).doFilter(request, response);
      assertThat(responseWriter.toString()).contains("SONARQUBE_ORG header is not allowed");
    }
  }

  static Stream<String> organizationHeaders() {
    return Stream.of(
      null,
      "  ",
      "my-org",
      "other-org"
    );
  }

  @Test
  void should_allow_request_with_valid_read_only_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_token");
    when(request.getHeader("SONARQUBE_READ_ONLY")).thenReturn("true");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }

  @Test
  void should_reject_request_with_invalid_read_only_header() throws Exception {
    var filter = tokenFilter(null);
    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader("Authorization")).thenReturn("Bearer squ_token");
    when(request.getHeader("SONARQUBE_READ_ONLY")).thenReturn("yes");

    filter.doFilter(request, response, filterChain);

    verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    verify(filterChain, never()).doFilter(request, response);
    assertThat(responseWriter.toString()).contains("Invalid SONARQUBE_READ_ONLY header value");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Bearer", "bearer", "bEaReR"})
  void it_should_dispatch_only_verified_cloud_credentials_and_scopes(String scheme) throws Exception {
    var authentication = new OAuthRequestAuthentication("cloud-token-b", Set.of("write:all"));
    var receivedTokens = new ArrayList<String>();
    var filter = oauthFilter(OAUTH_METADATA, token -> {
      receivedTokens.add(token);
      return authentication;
    });
    oauthHeaders(scheme + " mcp.token.a");

    filter.doFilter(request, response, filterChain);

    assertThat(receivedTokens).containsExactly("mcp.token.a");
    var trustedAuthentication = ArgumentCaptor.forClass(HttpRequestAuthentication.class);
    verify(request).setAttribute(eq(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE), trustedAuthentication.capture());
    assertThat(trustedAuthentication.getValue().token()).isEqualTo("cloud-token-b");
    assertThat(trustedAuthentication.getValue().toString()).doesNotContain("cloud-token-b");
    assertThat(trustedAuthentication.getValue().scopes().permits(WRITE)).isTrue();
    assertThat(trustedAuthentication.getValue().scopes().permits(READ)).isFalse();
    verify(request).setAttribute(anyString(), any());
    var dispatchedResponse = ArgumentCaptor.forClass(ServletResponse.class);
    verify(filterChain).doFilter(eq(request), dispatchedResponse.capture());
    assertThat(dispatchedResponse.getValue()).isInstanceOf(OAuthScopeResponseWrapper.class);
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void it_should_keep_scope_denials_local_when_authentication_is_cached() throws Exception {
    var cachedAuthentication = new OAuthRequestAuthentication("cloud-token-b", Set.of("read:all"));
    var filter = oauthFilter(OAUTH_METADATA, token -> cachedAuthentication);
    oauthHeaders("Bearer mcp.token.a");
    var secondResponse = mock(HttpServletResponse.class);

    filter.doFilter(request, response, filterChain);
    filter.doFilter(request, secondResponse, filterChain);

    var authentications = ArgumentCaptor.forClass(HttpRequestAuthentication.class);
    verify(request, times(2)).setAttribute(eq(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE), authentications.capture());
    var firstScope = authentications.getAllValues().getFirst().scopes();
    var secondScope = authentications.getAllValues().getLast().scopes();
    assertThat(secondScope).isNotSameAs(firstScope);
    firstScope.deny(WRITE);
    assertThat(secondScope.requiredScopes()).isEmpty();
    var dispatchedResponses = ArgumentCaptor.forClass(ServletResponse.class);
    verify(filterChain, times(2)).doFilter(eq(request), dispatchedResponses.capture());
    ((HttpServletResponse) dispatchedResponses.getAllValues().getFirst()).setStatus(HttpServletResponse.SC_OK);
    ((HttpServletResponse) dispatchedResponses.getAllValues().getLast()).setStatus(HttpServletResponse.SC_OK);

    verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge(Set.of("mcp:tools:write")) + ", error=\"insufficient_scope\"");
    verify(response, atLeastOnce()).setHeader("Cache-Control", "no-store");
    verify(secondResponse).setStatus(HttpServletResponse.SC_OK);
    verify(secondResponse, never()).setHeader(eq("WWW-Authenticate"), anyString());
  }

  @Test
  void it_should_reject_an_oauth_candidate_in_the_legacy_header() throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders();
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("mcp.token.a");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge() + ", error=\"invalid_token\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Basic credentials", "Bearer", "Bearer ", " Bearer token", "Bearer token token", "Bearer token,other",
    "Bearer token\t", "Bearer token\r\n", "Bearer token=other"})
  void it_should_reject_malformed_oauth_authorization_headers(String header) throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders(header);

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge() + ", error=\"invalid_token\"");
  }

  @Test
  void it_should_reject_multiple_authorization_headers() throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders("Bearer first-token", "Bearer second-token");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge() + ", error=\"invalid_token\"");
    assertThat(responseWriter.toString()).doesNotContain("first-token", "second-token");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "fixed-org"})
  void it_should_reject_every_oauth_organization_header(String organization) throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders("Bearer mcp.token.a");
    when(request.getHeader("SONARQUBE_ORG")).thenReturn(organization);

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_BAD_REQUEST);
    assertThat(responseWriter.toString()).contains("tool organization argument");
  }

  @Test
  void it_should_still_validate_the_read_only_preference_for_oauth_requests() throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders("Bearer mcp.token.a");
    when(request.getHeader("SONARQUBE_READ_ONLY")).thenReturn("yes");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_BAD_REQUEST);
    assertThat(responseWriter.toString()).contains("Invalid SONARQUBE_READ_ONLY");
  }

  @ParameterizedTest
  @MethodSource("oauthFailures")
  void it_should_return_sanitized_service_errors(OAuthAuthenticationException error) throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, token -> {
      throw error;
    });
    oauthHeaders("Bearer private.mcp.token");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(error.status());
    assertThat(responseWriter.toString()).contains(error.getMessage()).doesNotContain("private.mcp.token");
    if (error.status() == 401) {
      verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge() + ", error=\"invalid_token\"");
    } else if (error.status() == 403) {
      verify(response).setHeader("WWW-Authenticate", OAUTH_METADATA.challenge() + ", error=\"insufficient_scope\"");
    } else {
      verify(response, never()).setHeader(eq("WWW-Authenticate"), anyString());
    }
  }

  static Stream<OAuthAuthenticationException> oauthFailures() {
    return Stream.of(OAuthAuthenticationException.unauthorized(), OAuthAuthenticationException.insufficientScope(),
      OAuthAuthenticationException.invalidExchange(), OAuthAuthenticationException.unavailable());
  }

  @Test
  void it_should_fail_closed_when_the_authenticator_returns_null() throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, token -> null);
    oauthHeaders("Bearer mcp.token.a");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD"})
  void it_should_allow_unauthenticated_local_metadata_discovery(String method) throws Exception {
    var filter = successfulOAuthFilter();
    when(request.getMethod()).thenReturn(method);
    when(request.getRequestURI()).thenReturn(OAuthProtectedResourceMetadata.PATH);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(response);
    verify(request, never()).setAttribute(anyString(), any());
  }

  @Test
  void it_should_require_authentication_for_posting_to_the_metadata_path() throws Exception {
    var filter = successfulOAuthFilter();
    oauthHeaders();
    when(request.getRequestURI()).thenReturn(OAuthProtectedResourceMetadata.PATH);

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void it_should_not_expose_a_local_metadata_path_for_external_discovery() throws Exception {
    var metadata = new OAuthProtectedResourceMetadata("https://api.example.com/mcp", "https://login.example.com/",
      "https://discovery.example.com/mcp");
    var filter = oauthFilter(metadata, token -> new OAuthRequestAuthentication("cloud-token", Set.of("read:all")));
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn(OAuthProtectedResourceMetadata.PATH);

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate", metadata.challenge());
  }

  @Test
  void it_should_allow_options_without_authenticating_for_oauth_requests() throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, token -> {
      throw new AssertionError("OPTIONS must not authenticate");
    });
    when(request.getMethod()).thenReturn("OPTIONS");

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"squ_token", "sqco_organization_token", "0123456789012345678901234567890123456789"})
  void it_should_accept_bearer_and_legacy_tokens_with_oauth_configured_without_using_oauth(String token) throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      throw new AssertionError("Opaque tokens must not use OAuth");
    });
    oauthHeaders("Bearer " + token);
    when(request.getHeader("SONARQUBE_ORG")).thenReturn("legacy-org");
    filter.doFilter(request, response, filterChain);

    oauthHeaders();
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(token);
    filter.doFilter(request, response, filterChain);

    var authentications = ArgumentCaptor.forClass(HttpRequestAuthentication.class);
    verify(request, times(2)).setAttribute(eq(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE), authentications.capture());
    assertThat(authentications.getAllValues()).allSatisfy(authentication -> {
      assertThat(authentication.token()).isEqualTo(token);
      assertThat(authentication.scopes()).isNull();
      assertThat(authentication.toString()).doesNotContain(token);
    });
    verify(filterChain, times(2)).doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eyJ", "eyJmalformed", "a.b", "a.b.c"})
  void it_should_never_fall_back_to_a_legacy_token_when_oauth_fails(String token) throws Exception {
    var receivedTokens = new ArrayList<String>();
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      receivedTokens.add(value);
      throw OAuthAuthenticationException.unauthorized();
    });
    oauthHeaders("Bearer " + token);
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("valid-pat");

    filter.doFilter(request, response, filterChain);

    assertThat(receivedTokens).containsExactly(token);
    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Basic credentials", "Bearer", "Bearer ", "Bearer  token", " Bearer token", "Bearer token token", "Bearer token,other"})
  void it_should_not_fall_back_from_malformed_authorization_with_oauth_configured(String authorization) throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      throw new AssertionError("Invalid credentials must not use OAuth");
    });
    oauthHeaders(authorization);
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("valid-pat");

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "one two", "one,two", "a.b.c", "eyJ"})
  void it_should_reject_invalid_or_oauth_legacy_credentials_with_oauth_configured(String token) throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      throw new AssertionError("Legacy credentials must not use OAuth");
    });
    oauthHeaders();
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(token);

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  void it_should_prefer_a_single_bearer_over_any_legacy_credential_with_oauth_configured() throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      throw new AssertionError("Opaque tokens must not use OAuth");
    });
    oauthHeaders("bEaReR valid-pat");
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn("eyJ.other.token");

    filter.doFilter(request, response, filterChain);

    verify(request).setAttribute(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE, new HttpRequestAuthentication("valid-pat", null));
    verify(filterChain).doFilter(request, response);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "SONARQUBE_TOKEN"})
  void it_should_reject_duplicate_selected_headers_with_oauth_configured(String header) throws Exception {
    var filter = oauthFilter(OAUTH_METADATA, value -> {
      throw new AssertionError("Duplicate credentials must not use OAuth");
    });
    oauthHeaders();
    when(request.getHeader(header)).thenReturn(header.equals("Authorization") ? "Bearer valid-pat" : "valid-pat");
    when(request.getHeaders(header)).thenAnswer(invocation -> enumeration(List.of("valid-pat", "other-pat")));

    filter.doFilter(request, response, filterChain);

    assertOAuthFailure(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"existing.server.token", "eyJexistingServerToken", "squ_token"})
  void it_should_preserve_both_token_headers_without_oauth_configuration(String token) throws Exception {
    var filter = tokenFilter("fixed-org");
    oauthHeaders("Bearer " + token);

    filter.doFilter(request, response, filterChain);
    when(request.getHeader("Authorization")).thenReturn(null);
    when(request.getHeader("SONARQUBE_TOKEN")).thenReturn(token);
    filter.doFilter(request, response, filterChain);

    verify(request, times(2)).setAttribute(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE, new HttpRequestAuthentication(token, null));
    verify(filterChain, times(2)).doFilter(request, response);
    verify(response, never()).setHeader(eq("WWW-Authenticate"), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void it_should_not_create_a_trusted_result_without_a_downstream_credential(String token) {
    assertThatThrownBy(() -> new HttpRequestAuthentication(token, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new HttpRequestAuthentication(null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void it_should_reject_incomplete_oauth_support_at_construction() {
    assertThatThrownBy(() -> new OAuthSupport(null, token -> new OAuthRequestAuthentication("cloud-token", Set.of("read:all"))))
      .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new OAuthSupport(OAUTH_METADATA, null)).isInstanceOf(NullPointerException.class);
  }

  private void oauthHeaders(String... values) {
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/mcp");
    when(request.getHeaders("Authorization")).thenAnswer(invocation -> enumeration(List.of(values)));
    when(request.getHeader("Authorization")).thenReturn(values.length == 0 ? null : values[0]);
  }

  private static AuthenticationFilter tokenFilter(@Nullable String serverOrg) {
    return new AuthenticationFilter(serverOrg, null);
  }

  private static AuthenticationFilter oauthFilter(OAuthProtectedResourceMetadata metadata,
    Function<String, OAuthRequestAuthentication> authenticator) {
    return new AuthenticationFilter(null, new OAuthSupport(metadata, authenticator));
  }

  private static AuthenticationFilter successfulOAuthFilter() {
    return oauthFilter(OAUTH_METADATA, token -> new OAuthRequestAuthentication("cloud-token-b", Set.of("read:all")));
  }

  private void assertOAuthFailure(int status) {
    verify(response).setStatus(status);
    verify(response).setContentType("application/json");
    verify(response, atLeastOnce()).setHeader("Cache-Control", "no-store");
    verifyNoInteractions(filterChain);
    verify(request, never()).setAttribute(anyString(), any());
    assertThat(responseWriter.toString()).contains("\"jsonrpc\":\"2.0\"", "\"id\":null");
  }

}
