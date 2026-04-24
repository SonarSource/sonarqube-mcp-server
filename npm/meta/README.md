# @sonarsource/sonarqube-mcp-server

A [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server that connects AI agents with [SonarQube Server](https://www.sonarsource.com/products/sonarqube/) or [SonarQube Cloud](https://www.sonarsource.com/products/sonarcloud/) for code quality and security analysis. Ships with a bundled JRE so there are no Java prerequisites on the host.

## Usage

Configure your MCP client to launch the server via `npx`.

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "npx",
      "args": ["-y", "@sonarsource/sonarqube-mcp-server"],
      "env": {
        "SONARQUBE_TOKEN": "<YOUR_TOKEN>",
        "SONARQUBE_ORG": "<YOUR_ORG>"
      }
    }
  }
}
```

For SonarQube Server, set `SONARQUBE_URL` instead of (or in addition to) `SONARQUBE_ORG`.

## Custom CA certificates

Set `SONARQUBE_MCP_CA_CERTS` (or `NODE_EXTRA_CA_CERTS`) to a `:` / `;`-separated list of PEM files; they are imported into a per-user Java truststore on startup.

## Storage

Persistent state is written under an OS-specific user-data directory unless `STORAGE_PATH` is set explicitly:

- Linux: `$XDG_DATA_HOME/sonarqube-mcp` (default `~/.local/share/sonarqube-mcp`)
- macOS: `~/Library/Application Support/sonarqube-mcp`
- Windows: `%LOCALAPPDATA%\sonarqube-mcp`

## Supported platforms

`linux-x64`, `linux-arm64`, `darwin-x64`, `darwin-arm64`, `win32-x64`, `win32-arm64`.

## License

Distributed under the [SonarSource Source-Available License v1](https://sonarsource.com/license/ssal/).
