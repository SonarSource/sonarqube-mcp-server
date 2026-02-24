# Stdio Transport Architecture

---

## Overview


The SonarQube MCP Server supports two transport modes:

- **Stdio Transport**: Direct process communication (stdin/stdout)
- **HTTP Transport**: Network-based communication using the MCP stateless HTTP transport

This document focuses on the **Stdio Transport**.

---

## Transport Layer

```
┌─────────────────────────────────────────────────────┐
│                  MCP Client                         │
│         (Cursor, VS Code, GitHub Copilot)           │
└────────────────┬────────────────────────────────────┘
                 │ spawn process
                 ▼
┌─────────────────────────────────────────────────────┐
│            SonarQube MCP Server                     │
│              (Java Process)                         │
│                                                     │
│  ┌───────────────────────────────────────────┐     │
│  │  StdioServerTransportProvider             │     │
│  │  - Reads from System.in                   │     │
│  │  - Writes to System.out                   │     │
│  └───────────────────────────────────────────┘     │
│                     │                               │
│                     ▼                               │
│  ┌───────────────────────────────────────────┐     │
│  │  MCP Session                              │     │
│  │  - JSON-RPC message handling              │     │
│  │  - Tool dispatch                          │     │
│  └───────────────────────────────────────────┘     │
│                     │                               │
│                     ▼                               │
│  ┌───────────────────────────────────────────┐     │
│  │  MCP Tools                                │     │
│  │  - Use global ServerApi instance          │     │
│  │  - SonarQube API calls                    │     │
│  └───────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────┘
                 │ stdin/stdout
                 ▼
┌─────────────────────────────────────────────────────┐
│                  MCP Client                         │
└─────────────────────────────────────────────────────┘
```

## Communication Flow

### 1. Server Startup

```
1. MCP Client reads configuration
   ├─> Command: docker run --init --pull=always -i --rm mcp/sonarqube
   ├─> Environment: SONARQUBE_TOKEN, SONARQUBE_ORG
   └─> Spawn child process

2. Server Process Initialization
   ├─> Parse environment variables
   ├─> Validate SONARQUBE_TOKEN is present
   ├─> Create ServerApi instance with token
   ├─> Initialize SonarQube connection
   │   ├─> Version check
   │   └─> Plugin synchronization
   ├─> Detect and connect to SonarQube for IDE bridge (optional)
   └─> Register MCP tools

3. Stdio Transport Setup
   ├─> Attach to System.in for reading
   ├─> Attach to System.out for writing
   └─> Signal ready to client
```

### 2. Request Processing

```
1. Client sends JSON-RPC request via stdin
   {
     "jsonrpc": "2.0",
     "method": "tools/call",
     "params": { "name": "search_my_sonarqube_projects" },
     "id": 1
   }

2. Server reads from stdin
   ├─> Parse JSON-RPC message
   ├─> Validate request format
   └─> Dispatch to tool handler

3. Tool Execution
   ├─> Access global ServerApi instance
   ├─> Execute SonarQube API calls (using server's token)
   ├─> Process response
   └─> Return result

4. Server writes response to stdout
   {
     "jsonrpc": "2.0",
     "result": { ... },
     "id": 1
   }

5. Client reads from stdout
   └─> Process response
```

---

## Comparison: Stdio vs HTTP

| Feature                      | Stdio                  | HTTP                  |
|------------------------------|------------------------|-----------------------|
| **Setup Complexity**         | ✅ Simple               | ⚠️ Moderate           |
| **Network Exposure**         | ✅ None                 | ⚠️ Optional           |
| **Multi-User**               | ❌ One process per user | ✅ Shared gateway      |
| **Token Handling**           | ✅ Environment variable | ✅ Per-request header  |
| **SonarQube for IDE Bridge** | ✅ Supported            | ❌ Disabled            |
| **Process Lifecycle**        | ✅ Automatic            | ⚠️ Manual             |
| **Resource Usage**           | ⚠️ One JVM per user    | ✅ Shared JVM          |
| **Use Case**                 | Individual developers  | Shared server/gateway |

**Recommendation:** Use stdio transport unless you specifically need multi-user gateway functionality.

---

## References

- [MCP Specification - Stdio Transport](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#stdio)
- [MCP Java SDK Documentation](https://modelcontextprotocol.io/sdk/java/mcp-overview)

---

