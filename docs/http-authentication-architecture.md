# HTTP Transport and Authentication Architecture

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Components](#architecture-components)
3. [Authentication Flow](#authentication-flow)
4. [Token Propagation](#token-propagation)
5. [Thread Model and Context Management](#thread-model-and-context-management)
6. [Security Considerations](#security-considerations)
7. [Configuration](#configuration)
8. [Troubleshooting](#troubleshooting)

---

## Overview

The SonarQube MCP Server supports two transport modes:

- **Stdio Transport**: Direct process communication (stdin/stdout)
- **HTTP Transport**: Network-based communication using MCP Streamable HTTP specification

This document focuses on the **HTTP Transport** and its authentication mechanism.

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
│        (uses token from RequestContext)             │
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
- **Purpose**: Thread-local storage for request-scoped data

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

2. AuthenticationFilter intercepts request
   ├─> Extract token from SONARQUBE_TOKEN header
   ├─> Validate token presence
   ├─> Store in RequestContext (InheritableThreadLocal)
   └─> Pass to next filter

3. McpSecurityFilter validates security
   ├─> Check Origin header
   ├─> Set CORS headers
   └─> Pass to servlet

4. MCP SDK servlet processes request
   ├─> Parse JSON-RPC message
   ├─> Dispatch to tool (async, new thread)
   └─> Child thread inherits RequestContext

5. Tool execution
   ├─> Retrieve token from RequestContext
   ├─> Create ServerApi with client's token
   └─> Call SonarQube API

6. Async completion
   ├─> AsyncListener triggered
   └─> RequestContext cleared
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

### Problem: Async Processing and ThreadLocal

The MCP SDK servlet uses **async processing** with **worker threads** to handle tool execution. This creates a challenge for `ThreadLocal` storage:

```
Request Thread (Filter)          Worker Thread (Tool)
      │                                 │
      ├─ Set RequestContext             │
      ├─ doFilter() ──────────────> Async dispatch
      └─ finally { clear() }            │
         [Context cleared!]              │
                                         ├─ Tool.execute()
                                         └─ RequestContext.get() ❌ NULL!
```

### Solution 1: InheritableThreadLocal

```java
private static final ThreadLocal<RequestContext> CONTEXT = new InheritableThreadLocal<>();
```

`InheritableThreadLocal` automatically propagates values from parent thread to child threads:

```
Request Thread                   Worker Thread
      │                                │
      ├─ Set RequestContext            │
      ├─ doFilter() ──────────────> Inherit context ✓
      │                                │
      │                                ├─ Tool.execute()
      │                                └─ RequestContext.get() ✓ Has token!
      │                                   │
      └─ [Wait for async completion]     └─ Complete
         │
         └─ AsyncListener.onComplete()
            └─ RequestContext.clear() ✓
```

### Solution 2: AsyncListener for Cleanup

The filter registers an `AsyncListener` to clean up the context **after** async processing completes.

This ensures:
1. Token is available during entire request processing
2. Context is properly cleaned up to prevent memory leaks
3. No thread pool contamination

### Why Not Store in Servlet Request Attributes?

Servlet request attributes don't propagate to worker threads either.

`InheritableThreadLocal` is the Java standard pattern for this use case.

---

## Security Considerations

### DNS Rebinding Protection

**Threat**: Attacker could use DNS rebinding to access local MCP server from remote website.

**Mitigation**: `McpSecurityFilter` validates `Origin` header:

**Allowed Origins**:
- When bound to `127.0.0.1`: Only `http://localhost`, `http://127.0.0.1`, etc.
- When bound to `0.0.0.0`: All origins allowed (less secure, logs warning)

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
