# Proxied MCP Servers Architecture

## Overview

The SonarQube MCP Server can integrate with proxied MCP servers to expose their tools through a unified interface. This allows the server to dynamically extend its capabilities by connecting to specialized MCP servers that provide additional functionality.
The list of proxied servers cannot be modified at runtime, this is only used for internal servers approved by Sonar.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│ SonarQubeMcpServer                                          │
│  ├─ Built-in Tools (analysis, projects, issues, etc.)      │
│  └─ Proxied Tools (from proxied MCP servers)               │
│      └─ ProxiedToolsLoader                                  │
│          ├─ ProxiedServerConfigParser                      │
│          ├─ McpClientManager                                │
│          │   └─ Multiple McpSyncClients                     │
│          └─ ProxiedMcpTool wrappers                         │
└─────────────────────────────────────────────────────────────┘
```

## Configuration

### Configuration File

Proxied MCP servers are defined in `/proxied-mcp-servers.json` (bundled in the JAR):

```json
[
  {
    "name": "my-server",
    "namespace": "myserver",
    "command": "node",
    "args": ["path/to/mcp-server.js"],
    "env": {}
  }
]
```

**Fields:**
- `name` (required): Human-readable server name (for logging)
- `namespace` (required): Tool name prefix (e.g., tool `analyze` becomes `context_analyze`)
- `command` (required): Executable command to start the MCP server
- `args` (optional): Command-line arguments
- `env` (optional): Environment variables (merged with parent process environment; config values override parent values)

### Tool Namespacing

Proxied tools are prefixed with their namespace to avoid conflicts:
- Server namespace: `context`
- Original tool name: `analyze_code`
- Exposed name: `context_analyze_code`

## Auto-Discovery

### Production Behavior

The server **always attempts to load all configured proxied servers** at startup:

1. Parse `proxied-mcp-servers.json`
2. Validate configuration
3. For each proxied server:
   - Attempt to execute the command
   - If successful: connect, discover tools, integrate them
   - If failed: log warning, continue without that server

**Example output:**
```
INFO: Initializing 2 proxied MCP server(s)...
INFO: Connected to 'caas' - discovered 5 tool(s)
WARN: Failed to initialize 'python-tools': command 'python' not found
INFO: Loaded 5 proxied tool(s) from 1/2 server(s)
```

**Key principle:** The server never fails to start due to proxied server issues. It gracefully degrades and continues with available servers.

## Adding a New Proxied Server

### 1. Add Configuration

Edit `src/main/resources/proxied-mcp-servers.json`:

```json
[
  {
    "name": "my-server",
    "namespace": "myserver",
    "command": "node",
    "args": ["path/to/mcp-server.js"],
    "env": {
      "NODE_ENV": "production"
    }
  }
]
```

### 2. Install Dependencies

Ensure the runtime environment has required dependencies:
- Update `Dockerfile` if needed
- Document dependencies in README

### 3. Test

Start the server - the proxied server will auto-discover:
- If dependencies are available → server loads successfully
- If dependencies are missing → warning logged, server continues

## Environment Variables

Proxied MCP servers automatically inherit **all environment variables** from the parent SonarQube MCP Server process. This means:

- If the main server has `ABC=xyz` in its environment, proxied servers can access it
- Config-specific `env` values **override** inherited parent environment variables
- This allows proxied servers to access secrets and configuration from the main server environment

**Example:**
```json
{
  "name": "my-server",
  "namespace": "myserver",
  "command": "python",
  "args": ["-m", "my_mcp_server"],
  "env": {
    "DEBUG": "true"
  }
}
```

If the main server is launched with:
```bash
export ABC=xyz
export DEBUG=false
export ANOTHER_VAR=value
java -jar sonar-mcp-server.jar
```

The proxied MCP server will receive:
- `ABC=xyz` (inherited from parent)
- `DEBUG=true` (overridden by config)
- `ANOTHER_VAR=value` (inherited from parent)
- Plus all other parent environment variables

## Security Considerations

- Configuration file is **bundled in JAR** and cannot be modified at runtime
- Proxied servers execute with same permissions as main server process
- Proxied servers inherit the parent process environment, including sensitive variables
- Config `env` values can override parent environment variables if needed
- Server commands must be trusted (arbitrary command execution)
