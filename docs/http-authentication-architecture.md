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
                 │ HTTP POST/GET/DELETE
                 │ Header: SONARQUBE_TOKEN
                 ▼
┌─────────────────────────────────────────────────────┐
│              Jetty HTTP Server                      │
│                (Port XXXX)                          │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            Servlet Filter Chain                     │
│  1. AuthenticationFilter (token extraction)         │
│  2. McpSecurityFilter (CORS + Origin validation)    │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│   HttpServletStreamableServerTransportProvider      │
│         (MCP SDK - handles protocol)                │
└────────────────┬────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────┐
│            MCP Tool Execution                       │
│     (looks up token from SessionTokenStore)         │
└─────────────────────────────────────────────────────┘
```

### 2. Key Classes

#### `HttpServerTransportProvider`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider`
- **Purpose**: Bootstraps Jetty server and configures servlet transport

#### `AuthenticationFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter`
- **Purpose**: Extracts and validates client tokens

#### `McpSecurityFilter`
- **Location**: `org.sonarsource.sonarqube.mcp.transport.McpSecurityFilter`
- **Purpose**: Security and CORS handling

#### `RequestContext`
- **Location**: `org.sonarsource.sonarqube.mcp.context.RequestContext`
- **Purpose**: Thread-local storage for session ID during tool execution

#### `SessionTokenStore`
- **Location**: `org.sonarsource.sonarqube.mcp.authentication.SessionTokenStore`
- **Purpose**: Secure session-to-token mapping store (prevents session hijacking)

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
   └─> Mcp-Session-Id: session-uuid

2. AuthenticationFilter intercepts request
   ├─> Extract token from SONARQUBE_TOKEN header
   ├─> Validate token presence
   ├─> Store/validate session-token binding in SessionTokenStore
   │   (prevents session hijacking - token must match for existing sessions)
   └─> Pass to next filter

3. McpSecurityFilter validates security
   ├─> Check Origin header
   ├─> Set CORS headers
   └─> Pass to servlet

4. MCP SDK servlet processes request
   ├─> Parse JSON-RPC message
   └─> Dispatch to tool handler

5. Tool execution handler
   ├─> Get session ID from MCP exchange
   ├─> Set session ID in RequestContext
   ├─> Execute tool

6. Tool execution (ServerApiProvider.get())
   ├─> Get session ID from RequestContext
   ├─> Look up token from SessionTokenStore by session ID
   ├─> Create ServerApi with client's token
   └─> Call SonarQube API
```

### 3. Authentication Modes

#### `TOKEN` Mode (Default)
- Clients provide their own SonarQube token
- Token validated by SonarQube API (not MCP server)
- Uses custom header format:
  - `SONARQUBE_TOKEN: <token>`

#### `OAUTH` Mode (Not Yet Implemented)
- OAuth 2.1 with PKCE
- Per MCP specification
- Future enhancement

---

## Token Propagation

### Problem: Async Processing, ThreadLocal, and Session Hijacking

The MCP SDK servlet uses **async processing** with **worker threads** to handle tool execution. This creates challenges:

1. **ThreadLocal leakage**: Worker threads from thread pools may have stale inherited ThreadLocal values
2. **Session hijacking**: Without proper binding, a malicious client could steal another user's session

### Solution: SessionTokenStore

Instead of relying on ThreadLocal for token storage, we use a `ConcurrentHashMap`-based store that maps session IDs to tokens:

```java
// SessionTokenStore - the source of truth
private final ConcurrentHashMap<String, String> sessionTokens = new ConcurrentHashMap<>();
```

The flow is:

```
1. HTTP Request arrives
   └─> Filter extracts token and session ID
   └─> SessionTokenStore.setTokenIfValid(sessionId, token)
       ├─> New session: stores token, returns true
       └─> Existing session: validates token matches, returns true/false
           (token mismatch = hijacking attempt → 403 Forbidden)

2. Tool Execution Handler
   └─> Gets session ID from MCP exchange
   └─> Sets session ID in RequestContext (ThreadLocal)

3. ServerApiProvider.get()
   └─> Gets session ID from RequestContext
   └─> Looks up token from SessionTokenStore
   └─> Creates ServerApi with the correct token
```

### Why This Design?

1. **Session-token binding**: A session can only be used with the token that created it
2. **No ThreadLocal contamination**: Tokens are never stored in ThreadLocal, only session IDs
3. **Explicit lookup**: Token is always looked up by session ID at point of use
4. **Thread-safe**: ConcurrentHashMap handles concurrent access correctly

---

## Security Considerations

### DNS Rebinding Protection

**Threat**: Attacker could use DNS rebinding to access local MCP server from remote website.

**Mitigation**: `McpSecurityFilter` validates `Origin` header:

**Allowed Origins**:
- When bound to `127.0.0.1` (default): Only `http://localhost`, `http://127.0.0.1`, etc.

> ⚠️ **Important**: The server defaults to binding to `127.0.0.1` (localhost) for security. This is the recommended configuration. Binding to other interfaces is not supported for production use.

### Token Security

**Server-side**:
- Token **never logged** in plain text
- Token **not validated** by MCP server (delegated to SonarQube API)
- Token **cleared** from memory after request

**Transport**:
- HTTPS recommended for production (not enforced for localhost development)
- Token in HTTP header (not URL query parameter)

**Storage**:
- Client responsible for secure token storage
- Token never persisted by MCP server

---

## References

- [MCP Specification - Streamable HTTP Transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
- [MCP Specification - Authorization](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java/mcp-overview)
- [Jakarta Servlet Specification](https://jakarta.ee/specifications/servlet/)

---
