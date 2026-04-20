# Config Generator

The page at [mcp.sonarqube.com](https://mcp.sonarqube.com) that helps users produce a ready-to-paste MCP configuration for their AI agent.

## Architecture

The entire wizard is driven by a single declarative JSON file. The HTML is a generic renderer.

```
docs/
├── config-flow.json                    # Source of truth: platforms, transports, agents, toolsets
├── config-generator.html               # Generic renderer (fetches the JSON at init)
├── llms.txt                            # AI- and human-readable reference (standalone)
├── index.html                          # Redirects to config-generator.html
├── CNAME                               # Custom domain (mcp.sonarqube.com)
├── assets/
│   ├── favicon.ico                     # Page icon
│   ├── Sonar_Mark_Dark Backgrounds.png # Nav logo shown in dark theme
│   ├── Sonar_Mark_Light Backgrounds.png# Nav logo shown in light theme
│   └── style.css                       # Design tokens + component styles (CLI-aligned)
└── config-generator-e2e/               # Playwright end-to-end tests
```

The page style is aligned with the [SonarQube CLI docs](https://cli.sonarqube.com): same palette (`#0f1117` dark / `#f3f4f6` light), Poppins headings, Inter body, `#126ED3` accent, sticky top nav with theme toggle, sentence-case throughout.

When a user changes a selection, the renderer:

1. Reads the active `platform`, `transport`, `agent` and options from the DOM.
2. Builds an env-vars map or an HTTP-headers map depending on the transport's `valueDelivery` field.
3. Formats the output using the agent's `output.format` descriptor (`json`, `cli-claude-stdio`, `cli-claude-http`, `toml`, `custom-json`, or `unsupported`).

## Running locally

Browsers block `fetch()` over `file://`, so you must serve the page via HTTP.

```bash
cd docs
python3 -m http.server 8080
# Then open http://localhost:8080/config-generator.html
```

Any static HTTP server works (`npx serve .`, `http-server`, etc.). GitHub Pages is already an HTTP server in production, so no extra config is needed there.

## Running the E2E tests (Playwright)

The Playwright suite auto-starts its own static server (see [`playwright.config.ts`](config-generator-e2e/playwright.config.ts)).

```bash
cd docs/config-generator-e2e
npm install                       # first time only
npx playwright install chromium   # first time only
npx playwright test               # run the suite (5 tests, ~7s)
```

Useful flags:

| Command                          | Purpose                               |
|----------------------------------|---------------------------------------|
| `npx playwright test --headed`   | Watch the browser drive the page      |
| `npx playwright test --ui`       | Interactive debugger with time travel |
| `npx playwright test --trace on` | Record a trace for later inspection   |

## Running the guard test (Java)

A JUnit test validates that `config-flow.json` stays in sync with `ToolCategory` and `McpServerLaunchConfiguration`. It fails the build if someone adds a new tool category without updating the JSON (or vice versa).

```bash
# From repository root
./gradlew test --tests "org.sonarsource.sonarqube.mcp.docs.ConfigFlowSyncTest"
```

Location: [`src/test/java/org/sonarsource/sonarqube/mcp/docs/ConfigFlowSyncTest.java`](../src/test/java/org/sonarsource/sonarqube/mcp/docs/ConfigFlowSyncTest.java).

## Adding things

### Add a new toolset

1. Add the enum value in [`ToolCategory.java`](../src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolCategory.java). If it should be enabled by default, also add it to `ToolCategory.defaultEnabled()`.
2. Add a matching entry to the `toolsets[]` array in [`config-flow.json`](config-flow.json):
   ```json
   { "key": "my-new-toolset", "label": "My New Toolset", "defaultEnabled": true }
   ```
3. Run `./gradlew test --tests "*ConfigFlowSyncTest*"` to confirm the JSON matches the enum.
4. Run the Playwright suite to confirm the checkbox renders.

### Add a new AI agent

Append to the `agents[]` array in [`config-flow.json`](config-flow.json). Pick the appropriate `format`:

| `format`                               | Use when the agent wants                                                  | Key fields                                                                      |
|----------------------------------------|---------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `json`                                 | A JSON config (Cursor, VS Code, Kiro, Windsurf, Gemini, Copilot, Generic) | `rootKey`, `urlKey`, `typeField`, `headersKey`, `extraFields`, `envValuePrefix` |
| `toml`                                 | A TOML config (Codex)                                                     | `sectionKey`, `envSectionKey`, `urlKey`, `headersKey`                           |
| `custom-json`                          | A custom key-mapped JSON (Zed)                                            | `keyMap`, `staticFields`                                                        |
| `cli-claude-stdio` / `cli-claude-http` | A CLI command (Claude)                                                    | Hardcoded in renderer                                                           |
| `unsupported`                          | Explicitly not supported in this mode                                     | `message`                                                                       |

Example -- a hypothetical agent "Foo" that wants `{ foo: { url, auth } }`:

```json
{
  "id": "foo",
  "label": "Foo IDE",
  "instructions": {
    "stdio": "📄 Add this to your <code>~/.foo/config.json</code>.",
    "http": "📄 Add this to your <code>~/.foo/config.json</code>."
  },
  "output": {
    "stdio": { "format": "json", "rootKey": "foo", "schema": "standard-docker" },
    "http":  { "format": "json", "rootKey": "foo", "urlKey": "url", "headersKey": "auth" }
  }
}
```

Then test with `npx playwright test` (the smoke test loads any agent by dropdown value).

### Add a new platform (e.g. a new SonarQube Cloud region)

Append to the `platforms[]` array. Add `implicitEnv` for any value that should always be set for that platform:

```json
{
  "id": "cloud-apac",
  "label": "SonarQube Cloud APAC",
  "fields": [
    { "id": "org", "envVar": "SONARQUBE_ORG", "label": "Organization Key", "required": true },
    { "id": "token", "envVar": "SONARQUBE_TOKEN", "label": "Token", "required": true, "secret": true }
  ],
  "implicitEnv": { "SONARQUBE_URL": "https://sonarqube.apac.example" },
  "tokenDocsUrl": "https://docs.sonarsource.com/..."
}
```

The guard test will verify all `envVar` references map to known constants in `McpServerLaunchConfiguration`.

## AI-readable reference (`llms.txt`)

Because `docs.sonarsource.com` content can change or be reorganized, the config generator ships with a self-contained plain-text reference ([llms.txt](llms.txt)) that describes every env var, transport mode, toolset, and agent output shape. Agents that can't or shouldn't hit external docs can fetch this file instead:

```
https://mcp.sonarqube.com/llms.txt
```

The filename follows the [SonarSource convention](https://cli.sonarqube.com/llms.txt) for LLM-oriented reference documents. Update this file whenever you change `config-flow.json` so the human-readable view stays in sync with the machine-readable one.

## Deployment

Pushing to `master` with changes under `docs/` triggers [`.github/workflows/deploy-docs.yml`](../.github/workflows/deploy-docs.yml), which publishes the folder to the `gh-pages` branch. GitHub Pages serves that branch at `https://mcp.sonarqube.com`.

The workflow triggers on changes to any of:
- `docs/config-generator.html`
- `docs/config-flow.json`
- `docs/index.html`
- `docs/llms.txt`
- `docs/CNAME`
- `docs/assets/**` (favicon, logo, stylesheet)

## Quick reference

```bash
# Manual smoke test
cd docs && python3 -m http.server 8080
# -> http://localhost:8080/config-generator.html

# End-to-end tests
cd docs/config-generator-e2e && npx playwright test

# Guard test (sync with Java source)
./gradlew test --tests "org.sonarsource.sonarqube.mcp.docs.ConfigFlowSyncTest"
```
