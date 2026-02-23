# HTTP Transport and Authentication Architecture

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Components](#architecture-components)
3. [Authentication Flow](#authentication-flow)
4. [Token Propagation](#token-propagation)
5. [Security Considerations](#security-considerations)

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
                 │ Header: SONARQUBE_TOKEN
                 │ Header: SONARQUBE_ORG (optional)
                 ▼
┌─────────────────────────────────────────────────────┐
│              Jetty HTTP Server                      │
│                (Port XXXX)                          │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            Servlet Filter Chain                     │
│  1. AuthenticationFilter (token validation)         │
│  2. McpSecurityFilter (CORS + Origin validation)    │
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
│            MCP Tool Execution                       │
│  (reads token from McpTransportContext ThreadLocal) │
└─────────────────────────────────────────────────────┘
```

### 2. Key Classes

#### `HttpServerTransportProvider`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider`
- **Purpose**: Bootstraps Jetty server and configures the stateless servlet transport with a context extractor that reads `SONARQUBE_TOKEN` and `SONARQUBE_ORG` headers into a `McpTransportContext` for each request

#### `AuthenticationFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter`
- **Purpose**: Validates that every request carries a non-blank `SONARQUBE_TOKEN` header. No session state is created or maintained.

#### `McpSecurityFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.McpSecurityFilter`
- **Purpose**: Security and CORS handling

#### `SonarQubeMcpServer` (ServerApiProvider)
- **Location**: `org.sonarsource.sonarqube.mcp.SonarQubeMcpServer`
- **Purpose**: In HTTP stateless mode, reads the current request's `McpTransportContext` from a `ThreadLocal` to extract the token and create a per-request `ServerApi` instance

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
1. Client sends HTTP POST with token header
   └─> SONARQUBE_TOKEN: squ_abc123
   └─> SONARQUBE_ORG: my-org (optional)

2. AuthenticationFilter intercepts request
   ├─> Extract token from SONARQUBE_TOKEN header
   ├─> Reject with 401 if token is missing or blank
   └─> Pass to next filter if token is present

3. McpSecurityFilter validates security
   ├─> Check Origin header
   ├─> Set CORS headers
   └─> Pass to servlet

4. HttpServletStatelessServerTransport processes request
   ├─> contextExtractor runs: reads SONARQUBE_TOKEN and SONARQUBE_ORG headers
   ├─> Creates McpTransportContext with those values
   ├─> Parse JSON-RPC message
   └─> Dispatch to tool handler (context available via ThreadLocal)

5. Tool execution (ServerApiProvider.get())
   ├─> Read McpTransportContext from ThreadLocal
   ├─> Extract sonarqube-token and sonarqube-org
   ├─> Create ServerApi with client's token
   └─> Call SonarQube API
```

### 3. Authentication Modes

#### `TOKEN` Mode (Default)
- Clients provide their own SonarQube token on **every request** (fully stateless)
- Token validated by SonarQube API (not the MCP server itself)
- Uses custom header format:
  - `SONARQUBE_TOKEN: <token>`
  - `SONARQUBE_ORG: <org>` (optional, overrides server-side default)

#### `OAUTH` Mode (Not Yet Implemented)
- OAuth 2.1 with PKCE
- Per MCP specification
- Future enhancement

---

## Token Propagation

### Design: Stateless Per-Request Token Extraction

The transport uses `HttpServletStatelessServerTransport` from the MCP Java SDK. For every incoming POST request, a `contextExtractor` function runs synchronously on the request thread and populates a `McpTransportContext` map with the request headers:

```java
HttpServletStatelessServerTransport.builder()
  .contextExtractor(request -> McpTransportContext.create(Map.of(
    "sonarqube-token", request.getHeader("SONARQUBE_TOKEN") != null ? request.getHeader("SONARQUBE_TOKEN") : "",
    "sonarqube-org",   request.getHeader("SONARQUBE_ORG")   != null ? request.getHeader("SONARQUBE_ORG")   : ""
  )))
  .build();
```

The MCP SDK makes this context available via a `ThreadLocal<McpTransportContext>` during tool execution, so `ServerApiProvider.get()` can access the token without any session lookup:

```
1. HTTP Request arrives
   └─> contextExtractor reads SONARQUBE_TOKEN / SONARQUBE_ORG from headers
   └─> McpTransportContext stored in ThreadLocal for this request thread

2. Tool Execution Handler
   └─> ThreadLocal<McpTransportContext> is already populated by the SDK

3. ServerApiProvider.get()
   └─> Reads McpTransportContext from ThreadLocal
   └─> Extracts token (and optionally org)
   └─> Creates a fresh ServerApi for this request
```

### Why This Design?

1. **Stateless**: No session-to-token mapping; each request is self-contained
2. **Horizontally scalable**: No sticky sessions required — any server instance can handle any request
3. **Simple**: No `ConcurrentHashMap` for session management, no session hijacking surface
4. **Per-request isolation**: A new `ServerApi` is created for each request using only the credentials from that request

---

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

- [MCP Specification - Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP Specification - Authorization](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java/mcp-overview)
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/)

---
