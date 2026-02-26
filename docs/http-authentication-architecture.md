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

This document focuses on the **HTTP/HTTPS Transport** and its authentication mechanism.

---

## Architecture Components

### 1. Transport Layer

```
┌─────────────────────────────────────────────────────┐
│                  MCP Client                         │
│            (Cursor, VS Code, etc.)                  │
└────────────────┬────────────────────────────────────┘
                 │ HTTP POST
                 │ Headers: SONARQUBE_TOKEN (required)
                 │          SONARQUBE_ORG (optional, SQC only)
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
│   (MCP SDK - stateless, extracts McpTransportCtx)  │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│       ToolsListFilteringHandler                     │
│  (per-request tools/list filtering)                 │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            MCP Tool Execution                       │
│  (reads token + toolset filters from               │
│   McpTransportContext ThreadLocal)                  │
└─────────────────────────────────────────────────────┘
```

### 2. Key Classes

#### `HttpServerTransportProvider`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider`
- **Purpose**: Bootstraps Jetty server and configures the stateless servlet transport with a context extractor that reads `SONARQUBE_TOKEN`, `SONARQUBE_ORG`, `SONARQUBE_TOOLSETS`, and `SONARQUBE_READ_ONLY` headers into a `McpTransportContext` for each request

#### `AuthenticationFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter`
- **Purpose**: Validates that every request carries a non-blank `SONARQUBE_TOKEN` header. No session state is created or maintained. Runs **after** `McpSecurityFilter` so CORS preflight (OPTIONS) requests and Origin validation are handled before authentication, allowing browsers to complete their preflight handshake without a token.

#### `McpSecurityFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.McpSecurityFilter`
- **Purpose**: Security and CORS handling (Origin validation, CORS headers)

#### `ToolsListFilteringHandler`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.ToolsListFilteringHandler`
- **Purpose**: Wraps the SDK's `McpStatelessServerHandler` to intercept `tools/list` responses and filter the returned tool list based on the per-request `SONARQUBE_TOOLSETS` and `SONARQUBE_READ_ONLY` headers from `McpTransportContext`. A well-behaved MCP client will only call tools it received from `tools/list`, so filtering the list is sufficient enforcement.

#### `SonarQubeMcpServer` (ServerApiProvider)
- **Location**: `org.sonarsource.sonarqube.mcp.SonarQubeMcpServer`
- **Purpose**: In HTTP stateless mode, reads the current request's `McpTransportContext` from a `ThreadLocal` to extract the token and create a per-request `ServerApi` instance.

---

## Authentication Flow

### 1. Client Configuration

Clients configure the HTTP endpoint with authentication:

```json
{
  "servers": {
    "sonarqube-http": {
      "url": "http://<host>:<port>/mcp",
      "headers": {
        "SONARQUBE_TOKEN": "your-sonarqube-token"
      }
    }
  }
}
```

### 2. Request Flow

```
1. Client sends HTTP POST with headers
   └─> SONARQUBE_TOKEN: squ_abc123          (required)
   └─> SONARQUBE_ORG: my-org                (optional, SQC only)
   └─> SONARQUBE_TOOLSETS: issues,rules     (optional, per-request override)
   └─> SONARQUBE_READ_ONLY: true            (optional, per-request override)

2. AuthenticationFilter intercepts request
   ├─> Extract token from SONARQUBE_TOKEN header
   ├─> Reject with 401 if token is missing or blank
   └─> Pass to next filter if token is present

3. McpSecurityFilter validates security
   ├─> Check Origin header
   ├─> Set CORS headers
   └─> Pass to servlet

4. HttpServletStatelessServerTransport processes request
   ├─> contextExtractor runs: reads SONARQUBE_TOKEN, SONARQUBE_ORG,
   │   SONARQUBE_TOOLSETS, and SONARQUBE_READ_ONLY headers
   ├─> Creates McpTransportContext with all extracted values
   ├─> Parse JSON-RPC message
   └─> Dispatch to ToolsListFilteringHandler

5. ToolsListFilteringHandler (tools/list only)
   ├─> Read SONARQUBE_TOOLSETS and SONARQUBE_READ_ONLY from McpTransportContext
   ├─> If per-request headers are present, filter tool list accordingly
   └─> Return filtered tools/list response (bypasses SDK's unfiltered response)

6. Tool execution - tools/call (ServerApiProvider.get())
   ├─> Read McpTransportContext from ThreadLocal
   ├─> Extract CONTEXT_TOKEN_KEY value
   ├─> Resolve org: use server-level env var (header must be absent) OR per-request header (required if env var not set)
   ├─> Create ServerApi with client's token and resolved org
   └─> Call SonarQube API
```

### 3. Authentication Modes

#### `TOKEN` Mode (Default)
- Clients provide their own SonarQube token on **every request** (fully stateless)
- Token validated by SonarQube API (not the MCP server itself)
- Uses custom header format:
  - `SONARQUBE_TOKEN: <token>` — required on every request
  - `SONARQUBE_ORG: <org>` — for SonarQube Cloud, identifies the organization. **Mutually exclusive with the server-level `SONARQUBE_ORG` env var**: if the env var is set at startup, clients must not send this header (results in an error); if the env var is not set, clients must send this header on every request
  - `SONARQUBE_TOOLSETS: <comma-separated-keys>` — optional; narrows the server-level toolset for this request (cannot add toolsets beyond what the server was launched with)
  - `SONARQUBE_READ_ONLY: true|false` — optional; can further restrict to read-only for this request (cannot lift a server-level read-only restriction)

#### `OAUTH` Mode (Not Yet Implemented)
- OAuth 2.1 with PKCE
- Per MCP specification
- Future enhancement

---

## Token Propagation

### Design: Stateless Per-Request Token Extraction

The transport uses `HttpServletStatelessServerTransport` from the MCP Java SDK. For every incoming POST request, a `contextExtractor` function runs synchronously on the request thread and populates a `McpTransportContext` map with the request headers.

The MCP SDK makes this context available via a `ThreadLocal<McpTransportContext>` during tool execution, so `ServerApiProvider.get()` can access the token and org without any session lookup:

```
1. HTTP Request arrives
   └─> contextExtractor reads SONARQUBE_TOKEN, SONARQUBE_ORG,
       SONARQUBE_TOOLSETS, and SONARQUBE_READ_ONLY headers
   └─> McpTransportContext stored in ThreadLocal for this request thread

2. ToolsListFilteringHandler (tools/list only)
   └─> Reads SONARQUBE_TOOLSETS and SONARQUBE_READ_ONLY from McpTransportContext
   └─> Filters the SDK's full tool list down to the per-request allowed subset

3. ServerApiProvider.get() (tools/call)
   └─> Reads McpTransportContext from ThreadLocal
   └─> Extracts token and org (strict: server-level env var XOR per-request header — mixing both is an error)
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

Per-request filtering is applied at the **`tools/list` response**: `ToolsListFilteringHandler` intercepts the SDK's response and removes tools that are not allowed for this request. The MCP client only sees tools it is permitted to use, reducing its context window accordingly. A well-behaved MCP client will only call tools it received from `tools/list`, so filtering the list is sufficient.

### Security Notes

- The `SONARQUBE_TOOLSETS` and `SONARQUBE_READ_ONLY` headers are **not** authentication headers. They are read by the SDK's `contextExtractor` inside the servlet — which only runs after both `McpSecurityFilter` and `AuthenticationFilter` have passed the request through. An unauthenticated request is rejected with HTTP 401 by `AuthenticationFilter` before the `contextExtractor` (and thus any filtering logic) runs.
- The raw header values are **never** echoed back in responses or error messages. Invalid toolset keys are silently discarded by `ToolCategory.parseCategories()`, preventing any header injection via error payloads.

### Example Client Configuration

```json
{
  "mcpServers": {
    "sonarqube-https": {
      "url": "https://your-server:8443/mcp",
      "headers": {
        "SONARQUBE_TOKEN": "<your-token>",
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

**Mitigation**: `McpSecurityFilter` validates the `Origin` header.

**Allowed Origins**:
- When bound to `127.0.0.1` (default): Only `http://localhost`, `http://127.0.0.1`, etc.

> ⚠️ **Important**: The server defaults to binding to `127.0.0.1` (localhost) for security. This is the recommended configuration. Binding to other interfaces is not supported for production use.

### Token Security

**Server-side**:
- Token **never logged** in plain text
- Token **not validated** by the MCP server (delegated to SonarQube API)
- No token is persisted between requests

**Transport**:
- HTTPS recommended for production (not enforced for localhost development)
- Token in HTTP header (not URL query parameter)

**Storage**:
- Client responsible for secure token storage
- Token is never persisted by the MCP server

---

## References

- [MCP Specification - HTTP Transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP Specification - Authorization](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java/mcp-overview)
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/)

---
