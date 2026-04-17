# Supported agents -- config formats & doc links

When an agent's MCP configuration format changes, check its docs link below, then update the agent's entry in `docs/config-flow.json` (the `output.stdio` / `output.http` descriptor). Changing the generic renderer in `docs/config-generator.html` is only needed if the agent requires a genuinely new output shape -- see the formatter table in [SKILL.md](SKILL.md).

## Hosted SonarQube Cloud MCP (embedded HTTP transport)

Client URL for the Sonar-hosted MCP endpoint (not the Cloud REST API base used internally by the Java server): EU `https://api.sonarcloud.io/mcp`, US `https://api.sonarqube.us/mcp`. These live in `transports[].urls` under the `sqc` entry of `config-flow.json`. Orchestration (when to send `SONARQUBE_ORG` as a header vs env) is documented in [SKILL.md](SKILL.md).

---

## antigravity — Google Antigravity

- **Doc**: https://antigravity.google/docs/mcp#how-to-connect
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ type: "http", url, headers? }`
- **Quirks**: none known

---

## claude — Claude Desktop / Claude Code

- **Claude Code doc**: https://docs.anthropic.com/en/docs/claude-code/mcp
- **Stdio format**: CLI — `claude mcp add --transport stdio sonarqube -- docker run ... -e KEY=VALUE ... mcp/sonarqube`
- **HTTP format**: CLI — `claude mcp add --transport http sonarqube <url> --header "K: V" ...`
- **Quirks**: CLI-based. **Do NOT use `--env`** for stdio — Claude's `--env` parser greedily consumes everything up to `--` as env values (including the server name), regardless of formatting. Pass env vars as `-e KEY=VALUE` directly in the Docker command instead. HTTP: `<name> <url>` come first, then `--header` flags after. Desktop config is generic JSON.

---

## cursor — Cursor IDE

- **Doc**: https://cursor.com/docs/mcp#installing-mcp-servers
- **Config file**: `.cursor/mcp.json`
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ type: "http", url, headers? }`
- **Quirks**: none known

---

## vscode — VS Code

- **Native MCP doc**: https://code.visualstudio.com/docs/copilot/chat/mcp-servers
- **Config file**: `.vscode/mcp.json` (native) or `cline_mcp_settings.json` (Roo Cline)
- **Config key**: `servers` ← **different from most others**
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ type: "http", url, headers? }`
- **Quirks**: top-level key is `servers`, not `mcpServers`

---

## windsurf — Windsurf

- **Doc**: https://docs.windsurf.com/windsurf/cascade/mcp#adding-a-new-mcp
- **Config file**: `~/.codeium/windsurf/mcp_config.json`
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ serverUrl, headers? }` ← uses `serverUrl`, not `url`
- **Quirks**: HTTP key is `serverUrl` instead of `url`; no `type` field

---

## zed — Zed IDE

- **Doc**: https://zed.dev/docs/ai/mcp
- **Config file**: Zed `settings.json`
- **Config key**: flat object with `sonarqube_token`, `sonarqube_url`, `sonarqube_org`, `docker_path`
- **HTTP support**: ❌ not supported — the Zed SonarQube extension only supports Stdio/Docker
- **Quirks**: completely different format; uses extension-specific keys, not standard MCP schema

---

## copilot-cli — GitHub Copilot CLI

- **Doc**: https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers (confirmed current)
- **Config file**: `~/.copilot/mcp-config.json` (global) or `.copilot/mcp-config.json` (workspace)
- **Config key**: `mcpServers`
- **Stdio format**: `{ type: "local", command, args, env, tools: ["*"] }`
- **HTTP format**: `{ type: "http", url, headers?, tools: ["*"] }`
- **Quirks**: requires `tools: ["*"]`; stdio uses `type: "local"`

---

## copilot-agent — GitHub Copilot Workspace Agent

- **Doc**: https://docs.github.com/en/copilot/customizing-copilot/extending-copilot-coding-agent-with-mcp
- **Config location**: GitHub repository settings → Copilot → Coding agent → MCP Servers
- **Config key**: `mcpServers`
- **Stdio format**: `{ type: "local", command, args, env: {KEY: "COPILOT_MCP_KEY"}, tools: ["*"] }`
- **HTTP format**: `{ type: "http", url, headers?, tools: ["*"] }`
- **Quirks**: env var values are remapped to `COPILOT_MCP_<KEY>` form; requires `tools: ["*"]`

---

## gemini — Gemini CLI

- **Doc**: https://github.com/google-gemini/gemini-cli/blob/main/docs/tools/mcp-server.md
- **Config file**: `~/.gemini/settings.json` (global) or `.gemini/settings.json` (project)
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ httpUrl, headers? }` ← uses `httpUrl` for Streamable HTTP (NOT `url` which is SSE only)
- **Quirks**: JSON config file, **not** a CLI command. `httpUrl` for HTTP streaming; `url` is SSE only.

---

## codex — Codex CLI (OpenAI)

- **Doc**: https://developers.openai.com/codex/mcp#connect-codex-to-an-mcp-server
- **Config file**: `~/.codex/config.toml` (global) or `.codex/config.toml` (project-scoped)
- **Stdio format**: TOML — `[mcp_servers.sonarqube]` with `command`, `args`; env in `[mcp_servers.sonarqube.env]`
- **HTTP format**: TOML — `url = "..."` with `http_headers = { "Key" = "value" }` as an **inline table** on the same `[mcp_servers.sonarqube]` section
- **Quirks**: TOML format, not JSON. `http_headers` is an inline table (not a sub-section `[...headers]`). For token-from-env-var, use `bearer_token_env_var = "MY_ENV"` instead.

---

## kiro — Kiro

- **Doc**: https://kiro.dev/docs/mcp/configuration/
- **Config file**: `.kiro/settings/mcp.json` (workspace) or `~/.kiro/settings/mcp.json` (global)
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ url, headers? }` ← **no `type` field** for remote servers
- **Quirks**: Remote HTTP config does NOT include a `type` field — just `url` and optional `headers`.

---

## generic — Generic / Other

- **Purpose**: fallback for any client accepting standard MCP JSON
- **Config key**: `mcpServers`
- **Format**: standard `{ command, args, env }` for stdio or `{ type: "http", url, headers? }` for HTTP
