---
name: config-generator-maintenance
description: Maintains docs/config-generator.html, the SonarQube MCP Server configuration generator page. Use when adding a new AI agent client, changing transport options, updating env vars, modifying the UI flow, or fixing per-agent output format. Includes canonical config format per agent and links to their official MCP docs.
---

# Config Generator Maintenance

The generator is a single self-contained file: `docs/config-generator.html` (HTML + CSS + JS, no framework).

For detailed per-agent documentation links, see [agents.md](agents.md).

## Architecture at a glance

```
state = { agent, env, transport, httpMode, sqcRegion }
          ↓
generateConfig()
  → isHttpClient  (http/https client mode, or sqc)  → httpHeaders{}
  → isHttpLaunch  (http/https launch mode)           → envVars{}
  → stdio                                            → envVars{}
          ↓
  per-agent switch → code string + instructions HTML
```

## State machine

| `state.transport` | `state.httpMode` | `isHttpClient` | `isHttpLaunch` | Step 4 mode                |
|-------------------|------------------|----------------|----------------|----------------------------|
| `stdio`           | —                | false          | false          | full (env vars)            |
| `http` / `https`  | `client`         | **true**       | false          | http-client (headers only) |
| `http` / `https`  | `launch`         | false          | **true**       | full (env vars)            |
| `sqc`             | —                | **true**       | false          | http-client (headers only) |

**SQC card visibility rule**: hidden when `state.env === 'server'`; if already selected, falls back to `stdio`.

## Adding a new transport option

1. Add a `<label class="radio-card">` with `id="card-{mode}"` and `onclick="setTransport('{mode}')"`.
2. Add a `<input type="radio" name="transport" value="{mode}">` inside it.
3. In `setTransport()`: extend the `['stdio','http','https','sqc']` array and add the mode's show/hide logic.
4. In `generateConfig()`: update `isHttpClient` / `isHttpLaunch` booleans if needed.
5. Update every `switch(state.agent)` case.

## Adding a new agent

1. Add `<option value="{id}">{Label}</option>` in the Step 1 `<select>`.
2. Add a `case '{id}':` block in the `switch(state.agent)` inside `generateConfig()`.
3. Follow the pattern below. Check [agents.md](agents.md) for the correct config format.

### Per-agent case pattern

```js
case 'myagent':
    instructions = `📄 <strong>MyAgent config</strong>: <how to apply it>.`;
    if (isHttpClient) {
        // JSON-based clients: use httpClientObj() — injects url, type, headers automatically
        code = JSON.stringify({mcpServers: {sonarqube: httpClientObj()}}, null, 2);
        // ⚠️ Exceptions: some clients have different HTTP schemas — check agents.md:
        //   - Kiro: { url, headers? } — no "type" field
        //   - Gemini: { httpUrl, headers? } — uses httpUrl not url
        //   - Codex: TOML with http_headers inline table
        // CLI-based clients (e.g. Claude Code): all options before server name
        // let cmd = `mytool mcp add --transport http`;
        // for (const [k,v] of Object.entries(httpHeaders)) cmd += ` \\\n  --header "${k}: ${v}"`;
        // cmd += ` \\\n  sonarqube ${getHttpClientUrl()}`;
    } else {
        // Stdio / launch: use envVars + getDockerArgs()
        let env = {};
        for (const [k,v] of Object.entries(envVars)) env[k] = v;
        code = JSON.stringify({mcpServers: {sonarqube: {command:"docker", args:getDockerArgs(), env}}}, null, 2);
    }
    break;
```

**Key helpers available inside `generateConfig()`:**

| Helper | Returns |
|---|---|
| `httpClientObj(extraFields?)` | `{url, type:"http", headers?, ...extra}` — always use for JSON HTTP configs |
| `getHttpClientUrl()` | The effective URL (SQC endpoint or user-entered host) |
| `getDockerArgs()` | `docker run …` args array with all `-e KEY` flags |
| `getJsonConfigStdio()` | JSON string for stdio docker config |
| `renderJsonForClient()` | JSON string, switches automatically on `isHttpClient` |

## HTTP client headers (all transports when `isHttpClient`)

```js
httpHeaders = {
  "Authorization": "Bearer <token>",      // always
  "SONARQUBE_TOOLSETS": "...",             // only if non-default selection
  "SONARQUBE_READ_ONLY": "true"            // only if checked
}
```

## Env vars reference (stdio / launch)

| Var                                   | When set                                                 |
|---------------------------------------|----------------------------------------------------------|
| `SONARQUBE_TOKEN`                     | always                                                   |
| `SONARQUBE_ORG`                       | cloud / cloud-us                                         |
| `SONARQUBE_URL`                       | cloud-us (`https://sonarqube.us`) or server (user value) |
| `SONARQUBE_DEBUG_ENABLED`             | debug checkbox                                           |
| `SONARQUBE_READ_ONLY`                 | read-only checkbox                                       |
| `SONARQUBE_TOOLSETS`                  | non-default toolset selection                            |
| `SONARQUBE_TRANSPORT`                 | `http` or `https` (launch mode only)                     |
| `SONARQUBE_HTTP_PORT`                 | launch mode only                                         |
| `SONARQUBE_HTTPS_KEYSTORE_PATH`       | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_KEYSTORE_PASSWORD`   | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PATH`     | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD` | HTTPS launch, if filled                                  |

## Step 4 (Toolsets & Advanced) visibility rules

- **Stdio / HTTP+HTTPS launch**: full card shown — all checkboxes visible including Debug and Custom SSL Certs.
- **HTTP/HTTPS client / SQC**: card shown but Debug and SSL Certs hidden; help note reads "These options are sent as HTTP request headers".
- **HTTP/HTTPS client mode**: `advancedCard` shown via `applyAdvancedCardMode('http-client')`.
- **SQC transport**: always `http-client` mode (no launch option).

## Checking for agent config format changes

When a client updates its MCP config format, check the docs link in [agents.md](agents.md), then update:
1. The `case '{id}':` block in `generateConfig()`.
2. The JSON shape or CLI flags (especially the top-level key: `mcpServers` vs `servers`).
3. Any agent-specific quirks noted in [agents.md](agents.md).

## Default toolsets

```js
const DEFAULT_TOOLSETS = [
  'analysis','projects','issues','security-hotspots',
  'quality-gates','rules','duplications','measures',
  'dependency-risks','coverage','external'
];
```

`SONARQUBE_TOOLSETS` is only emitted when the selection differs from these defaults.
