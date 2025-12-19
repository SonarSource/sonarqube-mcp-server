# External Tool Providers Architecture

## Overview

The SonarQube MCP Server can integrate with external MCP servers (called "tool providers") to expose their tools through a unified interface. This allows the server to dynamically extend its capabilities by connecting to specialized MCP servers that provide additional functionality.

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

### Key Classes

- **`ExternalToolsLoader`**: Orchestrates loading of external providers and their tools
- **`ExternalServerConfigParser`**: Parses provider configuration from bundled JSON file
- **`McpClientManager`**: Manages connections to multiple external MCP servers
- **`ExternalMcpTool`**: Wraps external tools with namespaced names for disambiguation
- **`UnifiedConfigValidator`**: Validates provider configurations

## Configuration

### Configuration File

External tool providers are defined in `/external-tool-providers.json` (bundled in the JAR):

```json
[
  {
    "name": "caas",
    "namespace": "context",
    "command": "uv",
    "args": ["run", "sonar-code-context-mcp"],
    "env": {}
  }
]
```

**Fields:**
- `name`: Human-readable provider name (for logging)
- `namespace`: Tool name prefix (e.g., tool `analyze` becomes `context_analyze`)
- `command`: Executable command to start the provider
- `args`: Command-line arguments (optional)
- `env`: Environment variables (optional)

### Tool Namespacing

External tools are prefixed with their namespace to avoid conflicts:
- Provider namespace: `context`
- Original tool name: `analyze_code`
- Exposed name: `context_analyze_code`

## Auto-Discovery

### Production Behavior

The server **always attempts to load all configured providers** at startup:

1. Parse `external-tool-providers.json`
2. Validate configuration
3. For each provider:
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

### Test Isolation

Tests use a separate configuration file to ensure deterministic behavior:

- **Production**: `src/main/resources/external-tool-providers.json` (contains real providers)
- **Tests**: `src/test/resources/external-tool-providers.json` (contains `[]`)

Java's classpath precedence ensures test resources override main resources, so **no providers are loaded during tests** regardless of the test environment.

### Testing External Providers

To test external provider integration:
1. Create a dedicated integration test
2. Provide a custom configuration file
3. Load it explicitly using `ExternalServerConfigParser.parse()`

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
    }
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

No code changes required!

## Error Handling

All errors are caught and logged without crashing the server:

- **Configuration parse error**: Log warning, continue with empty provider list
- **Validation error**: Log detailed errors, continue without that provider
- **Connection failure**: Log warning, continue with remaining providers
- **Tool execution error**: Handled by individual tool, doesn't affect other tools

## Security Considerations

- Configuration file is **bundled in JAR** and cannot be modified at runtime
- Providers execute with same permissions as main server process
- Environment variables in config should not contain secrets (use system env instead)
- Provider commands must be trusted (arbitrary command execution)
