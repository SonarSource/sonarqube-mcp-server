# External Tool Providers Architecture

## Overview

The SonarQube MCP Server can integrate with external MCP servers (called "tool providers") to expose their tools through a unified interface. This allows the server to dynamically extend its capabilities by connecting to specialized MCP servers that provide additional functionality.
The list of tool providers cannot be modified at runtime, this is only used for internal providers approved by Sonar.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│ SonarQubeMcpServer                                          │
│  ├─ Built-in Tools (analysis, projects, issues, etc.)      │
│  └─ External Tools (from external providers)               │
│      └─ ExternalToolsLoader                                 │
│          ├─ ExternalServerConfigParser                     │
│          ├─ McpClientManager                                │
│          │   └─ Multiple McpSyncClients                     │
│          └─ ExternalMcpTool wrappers                        │
└─────────────────────────────────────────────────────────────┘
```

## Configuration

### Configuration File

External tool providers are defined in `/external-tool-providers.json` (bundled in the JAR):

```json
[
  {
    "name": "my-provider",
    "namespace": "myprovider",
    "command": "node",
    "args": ["path/to/mcp-server.js"],
    "env": {
      "NODE_ENV": "production"
    },
    "supportedTransports": ["stdio"]
  }
]
```

**Fields:**
- `name` (required): Human-readable provider name (for logging)
- `namespace` (required): Tool name prefix (e.g., tool `analyze` becomes `context_analyze`)
- `command` (required): Executable command to start the provider
- `args` (optional): Command-line arguments
- `env` (optional): Environment variables (merged with parent process environment; config values override parent values)
- `supportedTransports` (required): Array of transport modes supported by this provider. Valid values: `"stdio"`, `"http"`. Providers are only loaded if they support the server's current transport mode.
- `instructions` (optional): Brief instructions to help AI assistants use this provider's tools effectively. These are automatically appended to the server's base instructions.

### Tool Namespacing

External tools are prefixed with their namespace to avoid conflicts:
- Provider namespace: `context`
- Original tool name: `analyze_code`
- Exposed name: `context_analyze_code`

## Auto-Discovery

### Production Behavior

The server loads compatible providers at startup based on transport mode:

1. Parse `external-tool-providers.json`
2. Validate configuration
3. Filter providers by transport compatibility (skip providers that don't support current transport mode)
4. For each compatible provider:
   - Attempt to execute the command
   - If successful: connect, discover tools, integrate them
   - If failed: log warning, continue without that provider

**Example output:**
```
INFO: Initializing 2 external tool provider(s)...
INFO: Connected to 'caas' - discovered 5 tool(s)
WARN: Failed to initialize 'python-tools': command 'python' not found
INFO: Loaded 5 external tool(s) from 1/2 provider(s)
```

**Key principle:** The server never fails to start due to external provider issues. It gracefully degrades and continues with available providers.

## Adding a New Provider

### 1. Add Configuration

Edit `src/main/resources/external-tool-providers.json`:

```json
[
  {
    "name": "my-provider",
    "namespace": "myprovider",
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

Start the server - the provider will auto-discover:
- If dependencies are available → provider loads successfully
- If dependencies are missing → warning logged, server continues

## Environment Variables

External providers automatically inherit **all environment variables** from the parent SonarQube MCP Server process. This means:

- If the main server has `ABC=xyz` in its environment, external providers can access it
- Config-specific `env` values **override** inherited parent environment variables
- This allows providers to access secrets and configuration from the main server environment

**Example:**
```json
{
  "name": "my-provider",
  "namespace": "myprovider",
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

The external provider will receive:
- `ABC=xyz` (inherited from parent)
- `DEBUG=true` (overridden by config)
- `ANOTHER_VAR=value` (inherited from parent)
- Plus all other parent environment variables

## Security Considerations

- Configuration file is **bundled in JAR** and cannot be modified at runtime
- Providers execute with same permissions as main server process
- External providers inherit the parent process environment, including sensitive variables
- Config `env` values can override parent environment variables if needed
- Provider commands must be trusted (arbitrary command execution)
