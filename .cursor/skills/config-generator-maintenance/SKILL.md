---
name: config-generator-maintenance
description: Maintains the SonarQube MCP Server configuration generator at mcp.sonarqube.com. Use when adding a new AI agent client, toolset, platform (cloud region), or transport; changing output format; or updating UI copy. The generator is data-driven, most changes are JSON edits.
---

# Config Generator Maintenance

The configuration generator is **data-driven**. A single JSON file describes every
platform, transport, agent, and toolset; the HTML is a generic renderer with ~5
output formatters. Adding a new agent, toolset, or platform is usually a JSON
edit -- no JS changes needed.

Style is aligned with the [SonarQube CLI docs](https://cli.sonarqube.com) (same
palette, Poppins/Inter/SF Mono fonts, top nav with theme toggle, `llms.txt`
reference file).

## Files

| File                                             | Purpose                                                                                                      |
|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `docs/config-flow.json`                          | **Source of truth**: platforms, transports, toolsets, agents, per-agent output descriptors, snippet defaults |
| `docs/config-generator.html`                     | Generic renderer (fetches the JSON, builds UI, runs the Value Collector + formatters)                        |
| `docs/assets/style.css`                          | Design system (dark + light themes) copied from the CLI docs                                                 |
| `docs/assets/favicon.ico`                        | Page icon                                                                                                    |
| `docs/assets/Sonar_Mark_Dark Backgrounds.png`    | Nav logo used in dark theme (default)                                                                        |
| `docs/assets/Sonar_Mark_Light Backgrounds.png`   | Nav logo used in light theme (swapped via CSS based on `[data-theme="light"]`)                               |
| `docs/index.html`                                | Redirect to `config-generator.html`                                                                          |
| `docs/llms.txt`                                  | Self-contained plain-text reference for AI agents (**ASCII-only**)                                           |
| `docs/CNAME`                                     | Custom domain (`mcp.sonarqube.com`)                                                                          |
| `docs/config-generator-e2e/`                     | Playwright end-to-end tests                                                                                  |
| [`agents.md`](agents.md)                         | Per-agent config formats, official doc links, quirks                                                         |
| `src/test/java/.../docs/ConfigFlowSyncTest.java` | Java guard test: JSON ↔ `ToolCategory` / env var constants                                                   |
| `.github/workflows/deploy-docs.yml`              | Deploys `docs/` to `gh-pages` on push to `master`                                                            |

## Architecture

```
 docs/config-flow.json  (JSON source of truth)
        │
        │ fetch()
        ▼
 docs/config-generator.html (generic renderer)
        │
        ├─ builds UI from JSON (agents <select>, platform segmented control,
        │   transport radio cards, toolset checkboxes, HTTPS keystore fields)
        │
        ├─ collectValues()  →  envVars{} or httpHeaders{}
        │     (based on active transport's valueDelivery: "env" | "headers")
        │
        └─ formatOutput(agent, vals)  →  string
              switch on agent.output[stdio|http].format:
                • json            (9 agents)
                • toml            (Codex)
                • custom-json     (Zed)
                • cli-claude-stdio / cli-claude-http  (Claude)
                • unsupported     (Zed HTTP)
```

## State machine

| `state.transport` | `state.httpMode` | `isHttpClient` | `isHttpLaunch` | Step 4 mode                |
|-------------------|------------------|----------------|----------------|----------------------------|
| `stdio`           | —                | false          | false          | full (env vars)            |
| `http` / `https`  | `client`         | **true**       | false          | http-client (headers only) |
| `http` / `https`  | `launch`         | false          | **true**       | full (env vars)            |
| `sqc`             | —                | **true**       | false          | http-client (headers only) |

**SQC card visibility rule**: hidden when `state.env === 'server'`. If SQC was active, `setEnv` falls back to `stdio`.

## Adding a new toolset

1. Add the enum value in `src/main/java/.../tools/ToolCategory.java`.
2. If it should be on by default, add it to `ToolCategory.defaultEnabled()`.
3. Append to `toolsets[]` in `docs/config-flow.json`:
   ```json
   { "key": "my-toolset", "label": "My toolset", "defaultEnabled": true }
   ```
   Optional fields:
   - `alwaysOn: true` — cannot be disabled (currently only `projects`).
   - `onlyForTransports: ["stdio", "http", "https"]` — hide entirely for other transports (used to gate SQC and stdio-only features).
   - `hint: "..."` — tooltip on the checkbox label.
4. Update `docs/llms.txt` section 4 (TOOLSETS) with the new row (ASCII only).
5. Run `./gradlew test --tests "*ConfigFlowSyncTest*"` — it verifies the JSON and the enum are in sync.
6. Run the Playwright suite (see below).

## Adding a new agent

1. Append to `agents[]` in `docs/config-flow.json`, **in alphabetical order by `label`**:
   ```json
   {
     "id": "myagent",
     "label": "My Agent",
     "instructions": {
       "stdio": "<strong>My Agent config</strong>: Add this to ...",
       "http":  "<strong>My Agent config</strong>: Add this to ..."
     },
     "output": {
       "stdio": { "format": "json", "rootKey": "mcpServers", "schema": "standard-docker" },
       "http":  { "format": "json", "rootKey": "mcpServers", "urlKey": "url",
                  "typeField": "http", "headersKey": "headers" }
     }
   }
   ```
2. Pick the right `output.format` -- see the formatter table below. **Reuse existing formats first**; only add a new formatter in JS if the agent truly needs a new output shape.
3. Document the agent in [`agents.md`](agents.md): official MCP doc link, config file path, key quirks (e.g. `rootKey`, `urlKey` variations, extra fields).
4. Update `docs/llms.txt` section 7 (AGENT-SPECIFIC OUTPUT FORMATS) with the new agent block.
5. Run the Playwright suite.

## Adding a new platform (e.g. a new SonarQube Cloud region)

Append to `platforms[]` in `docs/config-flow.json`:

```json
{
  "id": "cloud-apac",
  "label": "SonarQube Cloud APAC",
  "fields": [
    { "id": "org",   "envVar": "SONARQUBE_ORG",   "label": "Organization key", "required": true },
    { "id": "token", "envVar": "SONARQUBE_TOKEN", "label": "User token", "required": true, "secret": true }
  ],
  "implicitEnv": { "SONARQUBE_URL": "https://sonarqube.apac" },
  "tokenDocsUrl": "https://docs.sonarsource.com/..."
}
```

If the embedded SQC server covers this region, also add an entry under `transports[sqc].urls` and update `availableFor`.

## Adding a new transport

Only needed when the MCP server gains a new transport (a new way to talk to it, not a new agent). Append to `transports[]`:

```json
{
  "id": "mytransport",
  "label": "My transport",
  "description": "...",
  "valueDelivery": "env" | "headers",
  "availableFor": ["cloud", "cloud-us", "server"],
  "features": ["toolsets", "read-only", ...],
  "modes": { ... }   // only if the transport has client/launch sub-modes like http
}
```

`valueDelivery` is the key abstraction: `"env"` means the renderer builds Docker `-e` flags; `"headers"` means HTTP headers. The renderer doesn't need more logic.

## Output formatters

The 5 formatters live in `generateConfig()` / `formatOutput()` in `docs/config-generator.html`. Adding a sixth is a last resort.

| Format                                | Used by                                                                                             | Properties on the JSON `output` descriptor                                                    |
|---------------------------------------|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `json`                                | Cursor, VS Code, Windsurf, Gemini, Copilot CLI, Copilot cloud agent, Kiro, Antigravity, Generic (9 total) | `rootKey`, `urlKey`, `typeField`, `headersKey`, `extraFields`, `envValuePrefix`               |
| `toml`                                | Codex                                                                                               | `sectionKey`, `envSectionKey`, `urlKey`, `headersKey`, `headersInline`                        |
| `custom-json`                         | Zed                                                                                                 | `keyMap` (env-var → JSON-key), `staticFields`                                                 |
| `cli-claude-stdio`, `cli-claude-http` | Claude                                                                                              | hardcoded -- Claude bakes env vars into docker args for stdio, uses `--header` flags for HTTP |
| `unsupported`                         | Zed HTTP                                                                                            | `message`                                                                                     |

## Value Collector (transport-agnostic)

`collectValues()` in the HTML gathers what the user typed plus implicit defaults and returns:

```js
{
  envVars: {...},       // populated when valueDelivery = "env"
  httpHeaders: {...},   // populated when valueDelivery = "headers"
  isHttpClient, isHttpLaunch,
  certPath, workspaceMountHost,
  httpHost, httpPort, clientUrl,
  rawToken
}
```

Rules (implemented once, not per agent):

- `SONARQUBE_TOKEN` from the token input (or `snippetDefaults.SONARQUBE_TOKEN` placeholder).
- Each platform field contributes its own `envVar` (or `SONARQUBE_ORG` header for http-client).
- `platform.implicitEnv` merged (e.g. `SONARQUBE_URL: https://sonarqube.us` for Cloud US).
- `transport.implicitEnv` merged when launching (e.g. `SONARQUBE_TRANSPORT: http`).
- `SONARQUBE_TOOLSETS` only emitted when user deviates from default set (see below).
- Toolsets **hidden via `onlyForTransports`** are marked `disabled`, which causes them to be ignored by the default-deviation check -- see invariants.

## Toolset availability via `onlyForTransports`

Each toolset can restrict itself:

```json
{ "key": "cag",      "onlyForTransports": ["stdio"] }
{ "key": "analysis", "onlyForTransports": ["stdio", "http", "https"] }   // not SQC
{ "key": "sources",  "onlyForTransports": ["stdio", "http", "https"] }   // not SQC
```

Semantics: when the active transport is not in the list, `syncToolsetStates()` **hides** the checkbox (`display:none`) AND marks it `disabled`. Disabling is required so the "did the user deviate from defaults?" check in `getToolsetsValue()` ignores it -- otherwise SQC would spuriously emit `SONARQUBE_TOOLSETS=...` because default-enabled toolsets (analysis, cag) appear "unchecked".

## Snippet defaults (Pascal-case placeholders)

When the user leaves a field empty, the snippet shows a placeholder instead of an empty string. These placeholders are centralized in `config-flow.json`:

```json
"snippetDefaults": {
  "SONARQUBE_TOKEN":       "<YourSonarQubeUserToken>",
  "SONARQUBE_ORG":         "<YourSonarQubeOrganizationKey>",
  "SONARQUBE_URL":         "<YourSonarQubeServerURL>",
  "SONARQUBE_PROJECT_KEY": "<YourSonarQubeProjectKey>"
}
```

Convention: angle brackets + PascalCase (matches the SonarSource doc style).

## Theme toggle

Top-nav button (☀ / ☾) sets `data-theme="light"` on `<html>` and persists to `localStorage` under `mcpdoc-theme` (keyed to avoid colliding with the CLI's `clidoc-theme`). The early inline `<script>` in `<head>` applies the preference before paint to prevent flash.

## Invariants (enforced by tests)

- Every `ToolCategory` enum value has a matching entry in `toolsets[]`. (Java guard test)
- `defaultEnabled` flags match `ToolCategory.defaultEnabled()`. (Java guard test)
- `alwaysOn: true` is `projects`. (Java guard test)
- All `envVar` references in the JSON resolve to known constants. (Java guard test)
- Agents dropdown is alphabetical by `label`. (Playwright)
- **Product name**: always "SonarQube MCP Server" (never "SonarQube MCP server"). (Playwright)
- **`llms.txt` is pure ASCII** -- no em-dashes, arrows, smart quotes. Mojibake in Latin-1 pipelines is a real problem. (Playwright)
- Footer reads `© <year> SonarSource Sàrl` + Documentation + SonarQube CLI + llms.txt + GitHub. (Playwright)
- `config-flow.json` is **not** advertised in the UI (implementation detail).
- SQC + default toolsets → generated config omits `SONARQUBE_TOOLSETS` entirely. (Playwright)
- Claude stdio bakes env vars as `-e KEY=VALUE` into the docker command (not `--env`), because Claude's `--env` parser is unreliable. (See [`agents.md`](agents.md).)

## Style rules

- **Sentence case** everywhere except product names: "User token" not "User Token", "Target client" not "Target Client".
- **Product names always capitalized**: "SonarQube MCP Server", "SonarQube Cloud", "SonarQube Server".
- **"User token"** not just "token" -- Sonar has multiple token types and being explicit avoids confusion.
- **No emoticons** in UI copy or instructions.
- **Placeholders** use Pascal case in angle brackets (see `snippetDefaults` above).

## Environment variables reference (for stdio / launch)

| Var                                   | When set                                                                 |
|---------------------------------------|--------------------------------------------------------------------------|
| `SONARQUBE_TOKEN`                     | always (stdio / launch); as `Authorization: Bearer ...` for http-client |
| `SONARQUBE_ORG`                       | cloud / cloud-us; in headers for http-client, env for stdio / launch    |
| `SONARQUBE_URL`                       | cloud-us implicit (`https://sonarqube.us`); server (user-entered)       |
| `SONARQUBE_DEBUG_ENABLED`             | debug checkbox, stdio / launch only                                      |
| `SONARQUBE_READ_ONLY`                 | read-only checkbox                                                       |
| `SONARQUBE_PROJECT_KEY`               | Step 4 field when stdio or HTTP(S) launch (NOT http-client / SQC)        |
| `SONARQUBE_TOOLSETS`                  | emitted only when selection differs from `defaultEnabled` set            |
| `SONARQUBE_TRANSPORT`                 | `http` or `https` (launch only)                                          |
| `SONARQUBE_HTTP_PORT`                 | launch only                                                              |
| `SONARQUBE_HTTP_HOST`                 | launch only, emitted only when value differs from default `127.0.0.1`   |
| `SONARQUBE_HTTPS_KEYSTORE_PATH`       | HTTPS launch, if filled                                                  |
| `SONARQUBE_HTTPS_KEYSTORE_PASSWORD`   | HTTPS launch, if filled                                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PATH`     | HTTPS launch, if filled                                                  |
| `SONARQUBE_HTTPS_TRUSTSTORE_PASSWORD` | HTTPS launch, if filled                                                  |

## Workspace mount (stdio only)

When **Local execution (stdio)** is selected, Step 4 can add:

```
-v <hostAbsolutePath>:/app/mcp-workspace:rw
```

Required for `run_advanced_code_analysis` and for the `cag` (Context augmentation) toolset. The mount is built in `buildDockerArgs(vals)` and mirrored in `formatClaudeStdio(vals)`. Hidden whenever `state.transport !== 'stdio'`.

## Tests

**Java guard test** (runs in CI):

```bash
./gradlew test --tests "org.sonarsource.sonarqube.mcp.docs.ConfigFlowSyncTest"
```

**Playwright E2E** (local only, contributors):

```bash
cd docs/config-generator-e2e
npm ci
npx playwright install chromium   # once per machine
npm test
```

Playwright's `webServer` serves the parent `docs/` directory and loads `/config-generator.html`. Current suite: 21 tests covering smoke flow, per-transport behaviour, toolset filtering, Pascal-case placeholders, theme toggle, footer, llms.txt ASCII constraint, favicon, external stylesheet, alphabetical agent order, product-name capitalization, project key behaviour.

## Post-change checklist

After editing any of the doc files:

1. **Java guard test**: `./gradlew test --tests "*ConfigFlowSyncTest*"` -- catches JSON ↔ source drift.
2. **Playwright suite**: `cd docs/config-generator-e2e && npm test` -- catches UI / output shape drift.
3. **Smoke test manually**: `cd docs && python3 -m http.server 8080`, open `http://localhost:8080/config-generator.html`. `fetch()` needs HTTP so `file://` will NOT work.
4. **If you added an agent**: toggle it in the dropdown and verify BOTH stdio and http-client outputs by switching transports.
5. **If you added a toolset or platform**: update `docs/llms.txt` (ASCII only!) and confirm the Java guard test still passes.
6. **If you changed output copy**: re-read the sentence-case, product-name, "User token" style rules above; the Playwright capitalization guard catches the obvious slips.

## Deployment

Pushing to `master` with changes under `docs/` triggers `.github/workflows/deploy-docs.yml`, which publishes the folder to the `gh-pages` branch. GitHub Pages serves that branch at `https://mcp.sonarqube.com`.

Watched paths: `docs/config-generator.html`, `docs/config-flow.json`, `docs/index.html`, `docs/llms.txt`, `docs/CNAME`, `docs/assets/**`.
