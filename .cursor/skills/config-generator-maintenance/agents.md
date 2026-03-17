# Supported Agents — Config Formats & Doc Links

When an agent's MCP configuration format changes, check its docs link below, then update the corresponding `case` in `generateConfig()` inside `docs/config-generator.html`.

---

## antigravity — Google Antigravity

- **Doc**: https://antigravity.google/docs/mcp#how-to-connect
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ type: "http", url, headers? }`
- **Quirks**: none known

---

## claude — Claude Desktop / Claude Code

- **Claude Code doc**: https://code.claude.com/docs/en/mcp#installing-mcp-servers
- **Stdio format**: CLI — `claude mcp add --transport stdio <name> --env K="V" -- docker <args>`
- **HTTP format**: CLI — `claude mcp add --transport http <name> --header "K: V" <url>`
- **Quirks**: CLI-based; Desktop config is generic JSON. `--header` flag injects request headers.

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

- **Native MCP doc**: https://code.visualstudio.com/docs/copilot/customization/mcp-servers#_add-an-mcp-server
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

- **Doc**: https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers
- **Config file**: `~/.copilot/mcp-config.json` (global) or `.copilot/mcp-config.json` (workspace)
- **Config key**: `mcpServers`
- **Stdio format**: `{ type: "local", command, args, env, tools: ["*"] }`
- **HTTP format**: `{ type: "http", url, headers?, tools: ["*"] }`
- **Quirks**: requires `tools: ["*"]`; stdio uses `type: "local"`

---

## copilot-agent — GitHub Copilot Workspace Agent

- **Doc**: https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/extend-coding-agent-with-mcp#adding-an-mcp-configuration-to-your-repository
- **Config location**: GitHub repository settings → Copilot → Coding agent → MCP Servers
- **Config key**: `mcpServers`
- **Stdio format**: `{ type: "local", command, args, env: {KEY: "COPILOT_MCP_KEY"}, tools: ["*"] }`
- **HTTP format**: `{ type: "http", url, headers?, tools: ["*"] }`
- **Quirks**: env var values are remapped to `COPILOT_MCP_<KEY>` form; requires `tools: ["*"]`

---

## gemini — Gemini CLI

- **Doc**: https://geminicli.com/docs/tools/mcp-server/#how-to-set-up-your-mcp-server
- **Stdio format**: CLI — `gemini mcp add -e K="V" sonarqube docker <args>`
- **HTTP format**: CLI — `gemini mcp add --transport http --header "K: V" sonarqube <url>`
- **Quirks**: CLI-based; `--header` flag for request headers

---

## codex — Codex CLI (OpenAI)

- **Doc**: https://developers.openai.com/codex/mcp#connect-codex-to-an-mcp-server
- **Config file**: `~/.codex/config.toml`
- **Stdio format**: TOML — `[mcp_servers.sonarqube]` with `command`, `args`; env in `[mcp_servers.sonarqube.env]`
- **HTTP format**: TOML — `url = "..."` with optional `[mcp_servers.sonarqube.headers]` section
- **Quirks**: TOML format, not JSON; headers go in a separate `[...headers]` table

---

## kiro — Kiro

- **Doc**: https://kiro.dev/docs/mcp/#managing-mcp-servers
- **Config file**: `.kiro/settings/mcp.json` (workspace) or `~/.kiro/settings/mcp.json` (global)
- **Config key**: `mcpServers`
- **Stdio format**: `{ command, args, env }`
- **HTTP format**: `{ type: "http", url, headers? }`
- **Quirks**: none known; uses standard generic JSON schema

---

## generic — Generic / Other

- **Purpose**: fallback for any client accepting standard MCP JSON
- **Config key**: `mcpServers`
- **Format**: standard `{ command, args, env }` for stdio or `{ type: "http", url, headers? }` for HTTP
