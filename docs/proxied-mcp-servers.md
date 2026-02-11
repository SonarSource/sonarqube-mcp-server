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
    "env": {},
    "supportedTransports": ["stdio"]
  }
]
```

**Fields:**
- `name` (required): Human-readable server name (for logging)
- `namespace` (required): Tool name prefix (e.g., tool `analyze` becomes `context/analyze`)
- `command` (required): Executable command to start the MCP server
- `args` (optional): Command-line arguments
- `env` (optional): Environment variables with explicit values. These take precedence over inherited variables.
- `inherits` (optional): Array of environment variable names to inherit from the parent process. Only variables listed here will be inherited.
- `supportedTransports` (required): Array of transport modes supported by this provider. Valid values: `"stdio"`, `"http"`. Providers are only loaded if they support the server's current transport mode.
- `instructions` (optional): Brief instructions to help AI assistants use this provider's tools effectively. These are automatically appended to the server's base instructions.

### Tool Namespacing

Proxied tools are prefixed with their namespace to avoid conflicts with the main server's tools, following the [MCP SEP-986 naming convention](https://modelcontextprotocol.io/community/seps/986-specify-format-for-tool-names):
- Server namespace: `context`
- Original tool name: `analyze_code`
- Exposed name: `context/analyze_code`

**Tool Name Validation:**

All tool names (both proxied and internal) are validated according to MCP SEP-986:
- **Length:** 1-64 characters
- **Allowed characters:** Alphanumeric (a-z, A-Z, 0-9), underscore (_), dash (-)
- **Case-sensitive:** `getUser`, `GetUser`, and `GETUSER` are different tool names
- **No spaces or special characters:** Spaces, commas, @, #, etc. are not allowed

If a proxied server exposes a tool with an invalid name, it will be automatically skipped with an error logged.

## Auto-Discovery

### Production Behavior

The server **always attempts to load all configured proxied servers** at startup:

1. Parse `proxied-mcp-servers.json`
2. Validate configuration
3. Filter proxied servers by transport compatibility (skip servers that don't support current transport mode)
4. For each proxied server:
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
    },
    "supportedTransports": ["stdio"],
    "instructions": "Before analyzing code issues, always use myprovider_my_tool to retrieve relevant code snippets."
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

**Security Note:** Proxied MCP servers use an **explicit allowlist model** for environment variables. Only environment variables explicitly defined in the configuration are passed to proxied servers.

This provides security through explicit allowlisting while maintaining flexibility:
- Environment variables can be set explicitly in the `env` field
- Environment variables can be inherited from the parent process using the `inherits` field
- Sensitive credentials (like `SONARQUBE_TOKEN`) are only accessible if explicitly listed

### How It Works

**Example 1: Explicit values in config**
```json
{
  "name": "my-server",
  "namespace": "myserver",
  "command": "python",
  "args": ["-m", "my_mcp_server"],
  "env": {
    "DEBUG": "true",
    "CUSTOM_VAR": "value"
  }
}
```

The proxied server receives:
- `DEBUG=true` (explicit value from config)
- `CUSTOM_VAR=value` (explicit value from config)

**Example 2: Inheriting from parent environment**
```json
{
  "name": "my-server",
  "namespace": "myserver",
  "command": "python",
  "args": ["-m", "my_mcp_server"],
  "env": {
    "DEBUG": "true"
  },
  "inherits": ["SONARQUBE_TOKEN", "SONARQUBE_URL"]
}
```

If the parent process has `SONARQUBE_TOKEN=secret123` and `SONARQUBE_URL=https://sonar.example.com`, the proxied server receives:
- `DEBUG=true` (explicit value from config)
- `SONARQUBE_TOKEN=secret123` (inherited from parent)
- `SONARQUBE_URL=https://sonar.example.com` (inherited from parent)

**Example 3: Overriding inherited values**
```json
{
  "name": "my-server",
  "namespace": "myserver",
  "command": "python",
  "args": ["-m", "my_mcp_server"],
  "env": {
    "DEBUG": "true",
    "API_URL": ""
  },
  "inherits": ["API_URL", "API_TOKEN"]
}
```

If the parent process has `API_URL=https://prod.example.com` and `API_TOKEN=secret`, the proxied server receives:
- `DEBUG=true` (explicit value from config)
- `API_URL=` (empty string - explicit value overrides inherited value)
- `API_TOKEN=secret` (inherited from parent)

**Note:** Variables not listed in `env` or `inherits` are never passed, even if they exist in the parent environment.

If neither `env` nor `inherits` are specified in the configuration, the proxied server starts with an empty environment.

**Important:** Only list environment variables that the proxied server actually needs. This minimizes the attack surface and prevents accidental credential exposure.

## Security Considerations

- Configuration file is **bundled in JAR** and cannot be modified at runtime
- Proxied servers execute with same permissions as main server process
- **Environment variable isolation**: Proxied servers only receive environment variables explicitly defined in their configuration
  - Variables must be either set in the `env` field or listed in the `inherits` field
  - This explicit allowlist prevents accidental credential leakage to proxied servers
- Server commands must be trusted (arbitrary command execution)
