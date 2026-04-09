---
name: config-generator-maintenance
description: Maintains docs/config-generator.html, the SonarQube MCP Server configuration generator page. Use when adding a new AI agent client, changing transport options, updating env vars, modifying the UI flow, or fixing per-agent output format. Includes canonical config format per agent and links to their official MCP docs.
---

# Config Generator Maintenance

The generator is a single self-contained file: `docs/config-generator.html` (HTML + CSS + JS, no framework).

For detailed per-agent documentation links and hosted SonarQube Cloud MCP URLs, see [agents.md](agents.md).

## Division of responsibility

| Source | Owns |
|--------|------|
| [agents.md](agents.md) | Per-client config shape (JSON / CLI / TOML), top-level keys (`mcpServers` vs `servers`), official doc links, quirks |
| This skill | `docs/config-generator.html` state machine, `httpHeaders` vs `envVars`, `SQC_URLS`, Step 4 visibility, flow invariants, post-change checklist |

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
        //   - VS Code: top-level key `servers` not `mcpServers`
        //   - Windsurf: { serverUrl, headers? } — no `type`
        //   - Kiro: { url, headers? } — no "type" field
        //   - Gemini: { httpUrl, headers? } — uses httpUrl not url
        //   - Codex: TOML with http_headers inline table
        //   - Copilot CLI / Agent: type + tools: ["*"]; agent env remapping for workspace
        //   - Zed: HTTP not supported (extension is stdio/docker only)
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

## HTTP client headers (when `isHttpClient`)

Built in `generateConfig()` inside the `if (isHttpClient)` block (search `httpHeaders['Authorization']`).

```js
httpHeaders = {
  "Authorization": "Bearer <token>",       // always
  "SONARQUBE_ORG": "...",                  // Step 2 Cloud (EU or US) — required for stateless hosted SQC / multi-tenant Cloud
  "SONARQUBE_TOOLSETS": "...",             // only if non-default selection
  "SONARQUBE_READ_ONLY": "true"            // only if checked
}
```

**Hosted SQC URL (`SQC_URLS`)**: must be the **MCP** endpoint (`https://api.sonarcloud.io/mcp`, `https://api.sonarqube.us/mcp`), not the Cloud **REST API** path (`.../api`). The `/api` base is used internally by the Java server (`ServerApiHelper`); MCP clients connect to `/mcp`.

**Server semantics**: See [README.md](../../../README.md) section **Transport Modes** (HTTP/HTTPS): Bearer token; if `SONARQUBE_ORG` is not fixed at server launch, clients must send `SONARQUBE_ORG` on each request; optional `SONARQUBE_TOOLSETS` / `SONARQUBE_READ_ONLY` headers narrow scope. `SONARQUBE_PROJECT_KEY` is **not** a per-request MCP header in the current server — set it only via container env (stdio / launch), not in `httpHeaders` for remote clients.

## Env vars reference (stdio / launch)

| Var                                   | When set                                                 |
|---------------------------------------|----------------------------------------------------------|
| `SONARQUBE_TOKEN`                     | always                                                   |
| `SONARQUBE_ORG`                       | cloud / cloud-us                                         |
| `SONARQUBE_URL`                       | cloud-us (`https://sonarqube.us`) or server (user value) |
| `SONARQUBE_DEBUG_ENABLED`             | debug checkbox                                           |
| `SONARQUBE_READ_ONLY`                 | read-only checkbox                                       |
| `SONARQUBE_PROJECT_KEY`               | optional; Step 4 field when stdio or HTTP(S) launch (not HTTP client / SQC) |
| `SONARQUBE_TOOLSETS`                  | non-default toolset selection                            |
| `SONARQUBE_TRANSPORT`                 | `http` or `https` (launch mode only)                     |
| `SONARQUBE_HTTP_PORT`                 | launch mode only                                         |
| `SONARQUBE_HTTPS_KEYSTORE_PATH`       | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_KEYSTORE_PASSWORD`   | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PATH`     | HTTPS launch, if filled                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD` | HTTPS launch, if filled                                  |

## Docker volume: workspace mount (stdio only)

When **Local Execution (Stdio)** is selected, Step 4 can add:

`-v <hostAbsolutePath>:/app/mcp-workspace:rw`

This mount is **required** to use **`run_advanced_code_analysis`** and **Context Augmentation** (`cag` toolset) in practice: the server needs the project tree at `/app/mcp-workspace`. It is **not** an env var; it is extra `docker run` args from `getDockerArgs()` (and duplicated in the Claude stdio branch). Hidden whenever Step 4 is in `http-client` mode (HTTP/HTTPS client or SQC). See [README.md](../../../README.md) **Workspace Mount (Reducing Context Bloat)**.

## Step 4 (Toolsets & Advanced) visibility rules

- **Stdio / HTTP+HTTPS launch**: full card shown — all checkboxes visible including Debug, Custom SSL Certs, optional **Default project key** (`SONARQUBE_PROJECT_KEY`), and **Workspace mount** (mount row is shown only when `state.transport === 'stdio'`; still hidden in `http-client` card mode).
- **HTTP/HTTPS client / SQC**: card shown but Debug, SSL Certs, Default project key, and Workspace mount hidden; help note reads "These options are sent as HTTP request headers".
- **HTTP/HTTPS client mode**: `advancedCard` shown via `applyAdvancedCardMode('http-client')`.
- **SQC transport**: always `http-client` mode (no launch option).

## README cross-reference (transport semantics)

When changing HTTP client behavior, re-read the SonarQube MCP Server [README.md](../../../README.md):

- **Transport Modes** — stateless HTTP(S): `Authorization: Bearer`, org header rules vs server-pinned org, toolsets/read-only headers.
- **Workspace Mount (Reducing Context Bloat)** — `/app/mcp-workspace` bind mount for stdio Docker runs.
- Environment tables for launch (`SONARQUBE_TRANSPORT`, `SONARQUBE_HTTP_PORT`, `SONARQUBE_HTTP_HOST`, TLS vars, etc.).

The generator maps **UI state** (`state.env`, `state.transport`, `state.httpMode`) to those rules: Cloud + `isHttpClient` ⇒ headers (token, org, optional toolsets/readonly); stdio / HTTP launch ⇒ `envVars` only for token/org/url/debug/etc.

## Flow invariants (must stay true)

Verify by searching `generateConfig()` in `docs/config-generator.html` (not by stale line numbers):

- **`isHttpClient`** (`http`/`https` + client mode, or `sqc`): populate `httpHeaders` with `Authorization`; for Step 2 **Cloud** or **Cloud US** also `SONARQUBE_ORG` (same `org` variable as stdio); optional `SONARQUBE_TOOLSETS` / `SONARQUBE_READ_ONLY` per checkbox/toolset logic.
- **`!isHttpClient`** (stdio or HTTP/HTTPS **launch**): token and org/url/debug/toolsets/read-only live in `envVars` as today; do not rely on request headers for auth/org in those modes. Optional `SONARQUBE_PROJECT_KEY` from the Step 4 field when non-empty (`projectKey` input id).
- **Stdio + workspace mount**: `state.transport === 'stdio'`, `opt-workspace-mount` checked, and non-empty `workspaceHostPath` ⇒ docker args include a `-v` mapping that path to `/app/mcp-workspace:rw` (after cert `-v` if any). Claude stdio must mirror `getDockerArgs()` volume list. Product-wise this mount is **required** for `run_advanced_code_analysis` and Context Augmentation (`cag`).
- **Context Augmentation (`cag`)**: stdio transport only — `syncCagToolsetState()` disables `tool-cag` when `state.transport !== 'stdio'`; `getToolsetsValue()` skips disabled checkboxes so `cag` is never emitted for HTTP client / SQC / launch.
- **`SQC_URLS`**: hosted MCP base URLs ending in `/mcp`; update [agents.md](agents.md) if product URLs change.
- **SQC card**: hidden when `state.env === 'server'`; if transport was `sqc`, fall back to `stdio` (`setEnv`).
- **New README env/header**: any variable documented in README transport/env tables should appear in this skill’s **HTTP client headers** or **Env vars reference** (or both), with clear client vs server-launch semantics.

## Post-change checklist

After editing `docs/config-generator.html`:

1. Re-read `generateConfig()`: `isHttpClient`, `isHttpLaunch`, `httpHeaders`, `envVars`, `getHttpClientUrl`, `SQC_URLS`, `getToolsetsValue`, `projectKey` / `SONARQUBE_PROJECT_KEY`, `workspaceMountHost`, `getDockerArgs`, Claude stdio `dockerArgs`.
2. Confirm every `switch (state.agent)` branch still uses the correct path for HTTP (`httpClientObj`, Windsurf/Gemini/Kiro/Codex exceptions per [agents.md](agents.md)) vs stdio/launch.
3. Spot-check the **State machine** table in this skill (transport × `httpMode` × Step 4 mode).
4. If HTTP client headers or Cloud behavior changed, cross-check [README.md](../../../README.md) Transport Modes (org header vs server-pinned org).
5. Run the **Playwright** suite locally (optional but recommended before commit): see [Local E2E tests](#local-e2e-tests-playwright) below.

## Local E2E tests (Playwright)

The package at [`docs/config-generator-e2e`](../../docs/config-generator-e2e) drives the real page in Chromium and asserts on generated output (SQC `/mcp` URL, org header, stdio env, transport toggles, project key). **Not run in CI** — for contributors only.

```bash
cd docs/config-generator-e2e
npm ci
npx playwright install chromium   # once per machine / after Playwright upgrades
npm test
```

Playwright’s `webServer` serves the parent [`docs/`](../../docs) directory and loads `/config-generator.html`.

If `playwright install` fails with a TLS / certificate error (e.g. SSL inspection), fix corporate trust store or, **only for the install step**, use `NODE_TLS_REJECT_UNAUTHORIZED=0 npx playwright install chromium` (insecure — do not use routinely).

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
  'dependency-risks','coverage','cag'
];
```

`SONARQUBE_TOOLSETS` is only emitted when the selection differs from these defaults.

The `cag` toolset (Context Augmentation) is included in the defaults for **stdio** only: `tool-cag` is checked by default when `state.transport === 'stdio'`. For HTTP/HTTPS client, SQC, or HTTP(S) launch, the generator **disables** `tool-cag`, unchecks it, and omits `cag` from `SONARQUBE_TOOLSETS` (CAG is not available outside stdio MCP transport). **Reset Default Tools** runs `syncCagToolsetState()` then re-checks defaults only on enabled toolsets. Using CAG (and `run_advanced_code_analysis`) **requires** the workspace mount in stdio mode; keep UI hints aligned.
