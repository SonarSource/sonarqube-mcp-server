# HTTP Transport and Authentication Architecture

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Components](#architecture-components)
3. [Authentication Flow](#authentication-flow)
4. [Token Propagation](#token-propagation)
5. [Per-Request Tool Filtering](#per-request-tool-filtering)
6. [Security Considerations](#security-considerations)

---

## Overview

The SonarQube MCP Server supports three transport modes:

- **Stdio Transport** (Recommended for local development): Direct process communication (stdin/stdout)
- **HTTP Transport** (Not recommended): Unencrypted network communication
- **HTTPS Transport** (Recommended for production): Secure network-based communication with TLS encryption

This document focuses on the **HTTP/HTTPS Transport**. `SONARQUBE_HTTP_AUTH_MODE` selects `TOKEN` (default) or opt-in `OAUTH` for SonarQube Cloud. OAuth requires a public HTTPS endpoint; HTTP can be used behind a trusted TLS-terminating proxy.

---

## Architecture Components

### 1. Transport Layer

```
┌─────────────────────────────────────────────────────┐
│                  MCP Client                         │
│            (Cursor, VS Code, etc.)                  │
└────────────────┬────────────────────────────────────┘
                 │ HTTP POST    (MCP requests)
                 │ HTTP GET     (optional SSE stream attempt per Streamable HTTP spec)
                 │ HTTP OPTIONS (CORS preflight)
                 │ Headers: Authorization: Bearer <token> (required, preferred)
                 │          SONARQUBE_TOKEN (deprecated, TOKEN mode only)
                 │          SONARQUBE_ORG (optional, Cloud TOKEN mode only)
                 │          SONARQUBE_TOOLSETS (optional, per-request override)
                 │          SONARQUBE_READ_ONLY (optional, per-request override)
                 ▼
┌─────────────────────────────────────────────────────┐
│              Jetty HTTP Server                      │
│                (Port XXXX)                          │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            Servlet Filter Chain                     │
│  1. McpSecurityFilter (CORS + Origin validation)    │
│  2. AuthenticationFilter (token validation)         │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│     HttpServletStatelessServerTransport             │
│   (MCP SDK - stateless, extracts McpTransportCtx)   │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│       PerRequestToolFilteringHandler                │
│  (tools/list and tools/call authorization)          │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            MCP Tool Execution                       │
│  (reads token + toolset filters from                │
│   McpTransportContext ThreadLocal)                  │
└─────────────────────────────────────────────────────┘
```

### 2. Key Classes

#### `HttpServerTransportProvider`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider`
- **Purpose**: Bootstraps Jetty and extracts a fresh `McpTransportContext` per request. TOKEN mode reads the token and optional organization header. OAuth mode requires the authentication filter's trusted result and extracts only the exchanged Cloud token and its scopes. Both modes retain request tool preferences.

#### `AuthenticationFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter`
- **Purpose**: Checks TOKEN credentials or authenticates OAuth requests through `Auth0OAuthAuthenticationService`. OAuth accepts a single `Authorization: Bearer` header and never falls back to the deprecated token header. Runs **after** `McpSecurityFilter`, which handles CORS preflight and Origin validation. Local OAuth metadata is public only when configured for local serving; MCP requests remain subject to authentication.

#### `McpSecurityFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.McpSecurityFilter`
- **Purpose**: Security and CORS handling (Origin validation, CORS headers)

#### `PerRequestToolFilteringHandler`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.PerRequestToolFilteringHandler`
- **Purpose**: Enforces request tool preferences and verified OAuth scopes on both `tools/list` and `tools/call`. Insufficient-scope calls mark a request-local `OAuthScopeContext`; `OAuthScopeResponseWrapper` converts the SDK response into an HTTP 403 challenge without dispatching the tool.

#### `SonarQubeMcpServer` (ServerApiProvider)
- **Location**: `org.sonarsource.sonarqube.mcp.SonarQubeMcpServer`
- **Purpose**: In HTTP stateless mode, wraps tool execution with its own `ThreadLocal<McpTransportContext>`, cleaned up in `finally`. `ServerApiProvider.get()` reads it to create a per-request `ServerApi` instance.

---

## Authentication Flow

### 1. Client Configuration

In TOKEN mode, clients configure the HTTP endpoint using the preferred `Authorization: Bearer` header:

```json
{
  "servers": {
    "sonarqube-http": {
      "url": "http://<host>:<port>/mcp",
      "headers": {
        "Authorization": "Bearer your-sonarqube-token"
      }
    }
  }
}
```

> **Deprecated (TOKEN mode only)**: The `SONARQUBE_TOKEN` request header is still accepted but will be removed in a future version. Migrate to `Authorization: Bearer <token>`.

### 2. TOKEN Request Flow

```
1. Client sends HTTP POST with headers
   └─> Authorization: Bearer squ_abc123     (required, preferred)
   │   OR SONARQUBE_TOKEN: squ_abc123       (deprecated, backward compat)
   └─> SONARQUBE_ORG: my-org               (optional, SQC only)
   └─> SONARQUBE_TOOLSETS: issues,rules    (optional, per-request override)
   └─> SONARQUBE_READ_ONLY: true           (optional, per-request override)

2. McpSecurityFilter validates security
   ├─> Allow OPTIONS preflight without authentication (enables CORS handshake)
   ├─> Check Origin header
   ├─> Set CORS headers
   └─> Pass to next filter

3. AuthenticationFilter intercepts request
   ├─> Extract token from Authorization: Bearer header (preferred)
   │   OR fall back to deprecated SONARQUBE_TOKEN header (logs a warning)
   ├─> Reject with 401 if no valid token found
   ├─> Reject SONARQUBE_ORG headers when a deprecated server organization is configured
   ├─> Validate SONARQUBE_READ_ONLY header (must be 'true' or 'false' if present)
   └─> Pass to servlet if all checks pass

4. HttpServletStatelessServerTransport processes request
   ├─> contextExtractor runs: extracts token from Authorization: Bearer
   │   (or deprecated SONARQUBE_TOKEN), plus SONARQUBE_ORG,
   │   SONARQUBE_TOOLSETS, and SONARQUBE_READ_ONLY headers
   ├─> Apply the configured server organization, otherwise the optional request header
   ├─> Creates McpTransportContext with all extracted values
   ├─> Parse JSON-RPC message
   └─> Dispatch to PerRequestToolFilteringHandler

5. PerRequestToolFilteringHandler
   ├─> Read SONARQUBE_TOOLSETS and SONARQUBE_READ_ONLY from McpTransportContext
   ├─> Filter tools/list and reject disallowed tools/call requests
   └─> Preserve organization-aware schemas when no organization header is set

6. Tool execution - tools/call (ServerApiProvider.get())
   ├─> Read McpTransportContext from ThreadLocal
   ├─> Extract CONTEXT_TOKEN_KEY value
   ├─> Resolve org: optional request header; otherwise tool argument
   ├─> Create ServerApi with client's token and resolved org
   └─> Call SonarQube API
```

### 3. Authentication Modes

#### `TOKEN` Mode (Default)
- Clients provide their own SonarQube token on **every request** (fully stateless)
- Token validated by SonarQube API (not the MCP server itself)
- Headers:
  - `Authorization: Bearer <token>` — **preferred**, required on every request
  - `SONARQUBE_TOKEN: <token>` — **deprecated**, still accepted for backward compatibility; will be removed in a future version
  - Deprecated server-level `SONARQUBE_ORG` fixes the Cloud organization for every request and logs a startup warning. Client organization headers and tool `organization` arguments are rejected.
  - `SONARQUBE_ORG: <org>` — optional fixed SonarQube Cloud request organization when no server organization is configured. When set, tool `organization` arguments are omitted from schemas and rejected. Otherwise, agents can use `list_organizations` and the tool argument; tools requiring an unresolved organization return discovery guidance.
  - `SONARQUBE_TOOLSETS: <comma-separated-keys>` — optional; narrows the server-level toolset for this request (cannot add toolsets beyond what the server was launched with)
  - `SONARQUBE_READ_ONLY: true|false` — optional; can further restrict to read-only for this request (cannot lift a server-level read-only restriction)

#### OAuth Mode

Auth0 handles user sign-in and authorization with Proof Key for Code Exchange (PKCE). The MCP server acts as the protected resource and exchanges tokens on the user's behalf (OBO). See the [README configuration](../README.md#hosted-sonarqube-cloud-oauth) for the environment variables. This implementation accepts GitHub-backed users; SSO is deferred.

1. A request without credentials receives HTTP 401 and a `WWW-Authenticate` challenge pointing to protected-resource metadata. The document identifies the exact MCP resource, trusted Auth0 issuer, and supported `mcp:tools:read` and `mcp:tools:write` scopes.
2. The client obtains an MCP access token (**Token A**) from Auth0 and sends it as `Authorization: Bearer <token>` on every MCP request.
3. `Auth0TokenVerifier` validates A's RS256 signature, issuer, MCP audience, required claims, and expiry. `Auth0OAuthAuthenticationService` requires a supported GitHub user subject, an originating client, and a lifetime of at most 300 seconds. A must have no Cloud audience, actor chain (`act`), or organization claim (`org_id`), and must grant a nonempty set containing only `mcp:tools:read` and/or `mcp:tools:write`. Any other scope, including `read:all`, `write:all`, or OIDC scopes, is rejected.
4. `Auth0TokenExchange` authenticates the backend application to Auth0 and requests a Cloud token using native token exchange. It sends explicit subject/requested access-token types, the Cloud audience, and exactly the Cloud scopes mapped from A: `mcp:tools:read` becomes `read:all`, and `mcp:tools:write` becomes `write:all`. Redirects and retries are disabled, with a 5-second connection timeout and 10-second total call timeout.
5. The server validates the returned Cloud token (**Token B**): signature and time claims, the same user, backend client, exact Cloud audience, no organization claim, and a two-level actor chain identifying the backend followed by A's originating client. B must have a lifetime of at most 300 seconds and a nonempty scope set contained in A's mapped Cloud delegation.
6. `AuthenticationFilter` stores B and **B's actual scopes** in trusted request attributes, creates a fresh `OAuthScopeContext`, and wraps the response before dispatch. The transport extracts B and that same scope context for the request. Missing trusted authentication fails closed. A never enters the downstream Cloud API context.
7. Tool listing and execution enforce the verified grants. A permitted tool uses B and the call's selected organization to access Cloud APIs, which still enforce user permissions.

OAuth rejects the `SONARQUBE_ORG` header. Organization-aware tools use `organization` arguments, and `list_organizations` provides discovery with `mcp:tools:read`, exchanged for `read:all` on B. No organization is stored between requests. TOKEN mode retains its optional fixed-organization header.

##### Discovery and trust configuration

OAuth requires explicit HTTPS SonarQube Cloud and API URLs. The configured Cloud audience must match the API root. The MCP resource uses path `/mcp` on that same API origin; the Cloud audience and Auth0 issuer use path `/`. Trust URLs cannot contain credentials, query parameters, or fragments. Exchange client credentials are explicit and redacted from configuration diagnostics.

By default, `GET /.well-known/oauth-protected-resource/mcp` serves public metadata locally; `HEAD` is also supported. If `SONARQUBE_OAUTH_METADATA_URL` is set, 401/403 challenges advertise that external HTTPS document and no local copy is registered. The external operator must serve matching resource, issuer, and scope values. This setting does not change token audiences or the trusted issuer used for Auth0 discovery and signing keys.

##### Signing keys and bounded exchange reuse

The verifier fetches the configured issuer's JSON Web Key Set (JWKS), never a URL supplied by a token. A successful refresh replaces the complete key set for one hour. Fresh known keys bypass refresh throttling; concurrent refreshes share a result. Network refresh attempts, including failures, are limited to ten per minute, with 5-second connection/read timeouts. A failed refresh never extends snapshot validity, and expired snapshots require a successful refresh.

The exchange cache is isolated by A's authorization fingerprint, issuer, user, client, target audience, and scopes. Identical concurrent loads share one exchange, with at most 16 exchanges in flight. Failed exchanges are not cached. Entries expire at the earliest of A's expiry, B's expiry, or the configured TTL: 0–60 seconds (default 60; zero disables reuse). Cache size is 1–5000 entries (default 256). A is verified before every lookup and B before every return, including cache hits. The cache is held in memory and creates no MCP session.

---

## Token Propagation

### Design: Stateless Per-Request Token Extraction

The transport uses `HttpServletStatelessServerTransport` from the MCP Java SDK. For every incoming POST request, a `contextExtractor` function populates a fresh `McpTransportContext`. TOKEN mode extracts request headers as shown below. OAuth mode extracts the validated Cloud token and scopes from the trusted authentication result, never the incoming bearer token.

The SDK passes the context to the tool handler. `SonarQubeMcpServer.toStatelessSpec()` sets the server's own `ThreadLocal<McpTransportContext>` for tool execution and removes it in `finally`, so `ServerApiProvider.get()` can access the token and org without any session lookup:

```
1. HTTP Request arrives
   └─> contextExtractor extracts token from Authorization: Bearer header
       (falls back to deprecated SONARQUBE_TOKEN for backward compat)
       plus SONARQUBE_ORG, SONARQUBE_TOOLSETS, and SONARQUBE_READ_ONLY headers
   └─> SDK receives the request-specific McpTransportContext

2. PerRequestToolFilteringHandler
   └─> Reads request preferences and, in OAuth mode, verified scopes
   └─> Filters tools/list and authorizes tools/call before dispatch

3. SonarQubeMcpServer tool wrapper (tools/call)
   └─> Sets its ThreadLocal for tool execution and cleans it up in finally
   └─> ServerApiProvider.get() reads McpTransportContext from that ThreadLocal
   └─> TOKEN mode extracts the effective org: configured SONARQUBE_ORG is fixed (request header rejected);
       otherwise the optional header fixes the request organization
   └─> Creates a fresh ServerApi for this request
```

### Why This Design?

1. **Stateless**: No session-to-token mapping; each request is self-contained
2. **Horizontally scalable**: No sticky sessions required — any server instance can handle any request
3. **Simple**: No `ConcurrentHashMap` for session management, no session hijacking surface
4. **Per-request isolation**: A new `ServerApi` is created for each request using only the credentials from that request

---

## Per-Request Tool Filtering

In HTTP(S) mode, clients can **narrow** the set of visible tools on a per-request basis by sending optional HTTP headers. These headers apply additional filtering on top of the server-level `SONARQUBE_TOOLSETS` and `SONARQUBE_READ_ONLY` environment variables — they can only reduce the scope, never expand it:

- If the server was launched with `SONARQUBE_READ_ONLY=true`, write tools are absent from the server's tool set and cannot be re-enabled per-request.
- If the server was launched with a restricted `SONARQUBE_TOOLSETS`, per-request headers can select a subset of those toolsets, but cannot add toolsets beyond what the server was launched with.

### Headers

| Header | Description |
|--------|-------------|
| `SONARQUBE_TOOLSETS` | Comma-separated list of toolset keys to enable for this request (e.g., `issues,quality-gates`). Must be a subset of the server-level `SONARQUBE_TOOLSETS`; toolsets not enabled at server startup are silently ignored. The `projects` toolset is always included regardless of this header. |
| `SONARQUBE_READ_ONLY` | Set to `true` to restrict this request to read-only tools only. Has no effect if the server was already launched with `SONARQUBE_READ_ONLY=true` (write tools are already absent). |

### Filtering

`PerRequestToolFilteringHandler` applies the same restrictions to **`tools/list` and direct `tools/call` requests**. Checks run in this order: tool existence and ordinary category/read-only restrictions, OAuth scopes, per-tool enablement, then dispatch. Unknown or ordinarily filtered tools retain their existing errors.

OAuth scopes are independent of the `SONARQUBE_READ_ONLY` preference and are not client-supplied headers or published tool-schema fields. Tool requirements use Token B's Cloud scope names internally:

- Read-only tools require `read:all`.
- Other tools default to `write:all`.
- A tool can explicitly require both scopes when its operations need both.

`write:all` does not grant read access, and `read:all` does not grant write access. B may contain fewer scopes than requested by A's mapped delegation; only B's grants govern tool access. An inventory test pins the hosted tool set and its scope requirements. Stdio-only Vortex tools are not exposed through hosted HTTP.

An otherwise eligible call lacking scopes executes no tool or entitlement check. It returns a JSON-RPC error with actual HTTP **403**, `Cache-Control: no-store`, and a `WWW-Authenticate` challenge containing `error="insufficient_scope"`, the metadata URL, and that tool's required scopes translated back to `mcp:tools:read` / `mcp:tools:write` for the client. The request-local response wrapper preserves this status despite the stateless SDK's normal HTTP 200 response handling; denial state is never stored in the token cache or shared between requests.

### Security Notes

- The `SONARQUBE_TOOLSETS` and `SONARQUBE_READ_ONLY` headers are **not** authentication headers. They are read by the SDK's `contextExtractor` inside the servlet — which only runs after both `McpSecurityFilter` and `AuthenticationFilter` have passed the request through. An unauthenticated request is rejected with HTTP 401 by `AuthenticationFilter` before the `contextExtractor` (and thus any filtering logic) runs.
- The raw header values are **never** echoed back in responses or error messages. Invalid toolset keys are silently discarded by `ToolCategory.parseCategories()`, preventing any header injection via error payloads.

### Example Client Configuration

When no server organization is configured, this TOKEN-mode example fixes the request organization. Omit the header to select organizations through tool arguments. OAuth always uses tool arguments and rejects this header.

```json
{
  "mcpServers": {
    "sonarqube-https": {
      "url": "https://your-server:8443/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>",
        "SONARQUBE_ORG": "<your-org>",
        "SONARQUBE_TOOLSETS": "issues,quality-gates",
        "SONARQUBE_READ_ONLY": "true"
      }
    }
  }
}
```

## Security Considerations

### DNS Rebinding Protection

**Threat**: Attacker could use DNS rebinding to access a local MCP server from a remote website.

**Mitigation**: `McpSecurityFilter` validates the `Origin` header as a browser-layer backstop on local bindings. Bearer authentication remains the primary control.

**Origin enforcement** (returns 403 when triggered):
- Applies only on local bindings (`127.0.0.1`, `localhost`, `0.0.0.0`).
- Skipped when the request carries `Authorization: Bearer …` or the legacy `SONARQUBE_TOKEN` header (native MCP clients).
- Skipped on OAuth bootstrap paths without a token: `POST /mcp` (401 discovery) and `GET /.well-known/*` (Protected Resource Metadata).
- Not applied on remote host bindings (e.g. SonarQube Cloud hosted); use `SONARQUBE_HTTP_ALLOWED_ORIGINS` for browser CORS only.

**Allowed Origins** (for CORS response headers and local enforcement):
- When bound to `127.0.0.1` or `localhost` (default): localhost browser origins (`http://localhost`, `http://127.0.0.1`, `http://[::1]`, with any port).
- When bound to `0.0.0.0` (required for container port mapping): same CORS policy as localhost bindings.
- Additional origins can be whitelisted via `SONARQUBE_HTTP_ALLOWED_ORIGINS` (comma-separated full origin URLs, e.g. `https://sonarcloud.io, https://my-app.example.com`).

> ⚠️ **Important**: The server defaults to binding to `127.0.0.1` (localhost) for security. This is the recommended configuration for local development. `SONARQUBE_HTTP_HOST=0.0.0.0` is for container listen address only. Use `SONARQUBE_HTTP_ALLOWED_ORIGINS` to extend the origin allowlist when deploying behind a web application.

### Token Security

**Server-side**:
- Credentials and token values are redacted from diagnostics; authentication errors do not expose Auth0 responses or verifier internals.
- TOKEN-mode credentials are validated by SonarQube APIs. OAuth verifies A and B locally before using B downstream.
- OAuth cache entries contain only validated downstream credentials and are bounded by token expiry, TTL, and capacity. Neither token is written to persistent storage.

**Transport**:
- The public OAuth resource must use HTTPS, directly or through a trusted TLS-terminating proxy.
- Bearer tokens belong in HTTP headers, never URL query parameters.

**Storage**:
- Clients remain responsible for secure token storage.
- Backend exchange credentials must come from deployment secret management.

### Production Readiness

The automated tests use controlled signing keys and simulated Auth0/Cloud responses. Before production rollout, validate live Auth0 application/API configuration, native OBO behavior and actor claims, Cloud scope enforcement and user permissions, regional audiences, signing-key rotation, reverse-proxy routing and challenge headers, and OAuth discovery/consent/step-up with supported MCP clients. Live Auth0/SQC validation is a deployment requirement; it is not established by the repository tests.

---

## References

- [MCP Specification - HTTP Transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP Specification - Authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization)
- [Hosted MCP OAuth security design](https://github.com/SonarSource/sonarcloud-authentication/blob/2a4013861688c889d0eac06d16430c56e7589f86/docs/mcp-oauth-security-review-request.md), with the [60-second cache amendment](https://github.com/SonarSource/sonarcloud-authentication/pull/1792)
- [SC-59185](https://sonarsource.atlassian.net/browse/SC-59185)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java/mcp-overview)
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/)

---

Organization-aware tool calls capture one immutable `ServerApi`. In TOKEN mode, a configured server organization or optional organization header fixes the organization and rejects tool arguments; without either, tools select the organization through their argument. Nested requests reuse the selected API. Discovery is organization-free, and per-call selections are never persisted between requests. Analytics captures the selected API on the request thread before asynchronous dispatch. OAuth always selects organizations through tool arguments.
