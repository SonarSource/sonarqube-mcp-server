# External MCP Servers Integration

The SonarQube MCP Server can act as a client to other MCP servers running on the same machine, allowing you to expose tools from multiple MCP servers through a single unified interface.

## Overview

This feature enables the SonarQube MCP Server to:
- Connect to external MCP servers via STDIO transport
- Discover and proxy their tools
- Forward tool execution requests transparently
- Manage multiple external server connections

## Configuration

External MCP servers are configured using the `SONARQUBE_EXTERNAL_SERVERS` environment variable, which can be either:
1. A path to a JSON configuration file
2. A JSON string directly

### Configuration Format

```json
[
  {
    "name": "weather",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-everything"],
    "env": {
      "API_KEY": "your-api-key"
    }
  },
  {
    "name": "database",
    "command": "/usr/local/bin/mcp-database-server",
    "args": ["--port", "8080"],
    "env": {}
  }
]
```

### Configuration Fields

- **name** (required): A unique internal identifier for the external server
- **namespace** (optional): The prefix used for tool names. If not specified, defaults to the `name` value
- **command** (required): The command to execute to start the server
- **args** (optional): Array of command-line arguments
- **env** (optional): Environment variables to pass to the server process

## Tool Naming Convention

Tools from external servers are prefixed with the namespace to avoid naming conflicts:

Original tool: `get_forecast`
Namespace: `weather`
Exposed tool: `weather_get_forecast`

### Using Namespace for Custom Prefixes

You can use the `namespace` field to customize the tool prefix independently of the internal server name:

```json
[
  {
    "name": "code-context-service",
    "namespace": "context",
    "command": "uv",
    "args": ["run", "sonar-code-context-mcp"]
  }
]
```

This allows you to:
- Use descriptive internal names for logging and debugging
- Keep tool prefixes short and user-friendly
- Change the internal identifier without affecting tool names visible to users

## Usage Example

### Using a Configuration File

1. Create a file `external-servers.json`:

```json
[
  {
    "name": "filesystem",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    "env": {}
  }
]
```

2. Set the environment variable:

```bash
export SONARQUBE_EXTERNAL_SERVERS="/path/to/external-servers.json"
```

3. Start the SonarQube MCP Server:

```bash
java -jar sonarqube-mcp-server.jar
```

### Using Direct JSON Configuration

```bash
export SONARQUBE_EXTERNAL_SERVERS='[{"name":"weather","command":"npx","args":["-y","@modelcontextprotocol/server-everything"],"env":{}}]'
java -jar sonarqube-mcp-server.jar
```

### Docker Example

For SonarQube Cloud with external servers:

```bash
docker run -i --name sonarqube-mcp-server --rm \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  -e SONARQUBE_EXTERNAL_SERVERS='[{"name":"filesystem","command":"npx","args":["-y","@modelcontextprotocol/server-filesystem","/tmp"],"env":{}}]' \
  mcp/sonarqube
```

## Tool Categories

External tools are assigned to the `EXTERNAL` category. This category can be controlled using the `SONARQUBE_TOOLSETS` environment variable:

```bash
# Include external tools
export SONARQUBE_TOOLSETS="analysis,issues,projects,external"

# Exclude external tools (omit 'external' from the list)
export SONARQUBE_TOOLSETS="analysis,issues,projects"
```

## Limitations

1. **Transport**: Only STDIO transport is currently supported for external servers
2. **Process Management**: External server processes are managed by the SonarQube MCP Server lifecycle
3. **Error Handling**: If an external server fails to start, it will be logged but won't prevent the SonarQube MCP Server from starting
4. **Synchronous Only**: External tool execution is synchronous (blocking)

## Troubleshooting

### External Server Not Connecting

Check the logs at `STORAGE_PATH/logs/mcp.log` for detailed error messages. Common issues:

- **Command not found**: Ensure the command is in the PATH or use an absolute path
- **Permission denied**: Ensure the command has execute permissions
- **Missing dependencies**: Ensure all required dependencies (e.g., Node.js for npx) are installed

### Tools Not Appearing

- Verify the external server is listed in the initialization logs
- Check that the `EXTERNAL` category is enabled in `SONARQUBE_TOOLSETS`
- Ensure `SONARQUBE_READ_ONLY` is not set to `true` if the tool requires write operations

### Tool Execution Failures

- Check that the tool arguments are correctly formatted
- Verify the external server is still running (check logs)
- Ensure the tool name includes the correct prefix (e.g., `weather_get_forecast`)

## Architecture

### Component Diagram

```
┌─────────────────────────────────────┐
│   SonarQube MCP Server              │
│                                     │
│  ┌──────────────────────────────┐  │
│  │  McpClientManager            │  │
│  │                              │  │
│  │  ┌────────────────────────┐ │  │
│  │  │ ExternalMcpTool        │ │  │
│  │  │ (Proxy)                │ │  │
│  │  └────────────────────────┘ │  │
│  └──────────────────────────────┘  │
└─────────────────────────────────────┘
                 │
                 │ STDIO
                 ▼
    ┌──────────────────────────┐
    │  External MCP Server      │
    │  (e.g., filesystem,      │
    │   weather, database)     │
    └──────────────────────────┘
```

### Key Classes

- **`McpClientManager`**: Manages connections to external MCP servers
- **`ExternalMcpTool`**: Proxy tool that forwards requests to external servers
- **`ExternalServerConfigParser`**: Parses JSON configuration
- **`ExternalMcpServerConfig`**: Configuration record for external servers

## Examples

### Example 1: Filesystem Server

```json
[
  {
    "name": "fs",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user/projects"],
    "env": {}
  }
]
```

Available tools will be prefixed with `fs_`, such as:
- `fs_list_directory`
- `fs_read_file`
- `fs_write_file`

### Example 2: Multiple Servers

```json
[
  {
    "name": "weather",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-everything"],
    "env": {
      "WEATHER_API_KEY": "your-key"
    }
  },
  {
    "name": "github",
    "command": "/usr/local/bin/github-mcp-server",
    "args": ["--token", "${GITHUB_TOKEN}"],
    "env": {
      "GITHUB_TOKEN": "ghp_xxxxxxxxxxxxx"
    }
  }
]
```

This will expose tools from both servers:
- `weather_get_forecast`, `weather_get_temperature`, etc.
- `github_list_repos`, `github_create_issue`, etc.

## Security Considerations

1. **Command Execution**: The server executes arbitrary commands. Only configure trusted external servers.
2. **Environment Variables**: Sensitive data (API keys, tokens) in `env` fields will be passed to child processes.
3. **File Access**: External servers may have access to the filesystem depending on their implementation.
4. **Network Access**: External servers may make network requests based on their functionality.

## Best Practices

1. **Naming**: Use short, descriptive names for external servers (e.g., "fs", "db", "api")
2. **Configuration Files**: Prefer configuration files over direct JSON strings for better maintainability
3. **Error Handling**: Monitor logs for external server issues
4. **Testing**: Test external server integration in a development environment first
5. **Documentation**: Document which external servers are configured and what tools they provide

## Related Configuration

- `SONARQUBE_TOOLSETS`: Control which tool categories are enabled (including `external`)
- `SONARQUBE_READ_ONLY`: Affects whether external tools with write operations are available
- `STORAGE_PATH`: Location where logs are written, useful for troubleshooting external server issues

