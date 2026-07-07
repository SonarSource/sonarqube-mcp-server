# SonarQube MCP Server

Java 21 Gradle application that exposes SonarQube Cloud and SonarQube Server to AI agents through the [Model Context Protocol](https://modelcontextprotocol.io/). Entry point: `org.sonarsource.sonarqube.mcp.SonarQubeMcpServer`.

## Principles

- Read surrounding code before editing; match existing patterns and naming.
- Keep changes minimal and scoped to the task — no drive-by refactors.
- Provide unit tests for every code change.
- Do not commit secrets, tokens, or credentials.
- External pull requests are not accepted; this guide supports internal development and forks.

## Prerequisites

- JDK 21
- Docker (only for `its` integration tests)

## Build & Verify

```bash
./gradlew clean build          # compile + unit tests
./gradlew test                 # unit tests only
./gradlew test --tests "org.sonarsource.sonarqube.mcp.tools.branches.ListBranchesToolTests"
./gradlew :its:integrationTest # integration tests (requires Docker; not part of default build)
./gradlew :its:integrationTest --tests "org.sonarsource.sonarqube.mcp.its.StdioTransportITest"
```

After adding or updating dependencies, regenerate lock files (requires Artifactory credentials locally):

```bash
./gradlew :dependencies --write-locks
./gradlew :its:dependencies --write-locks
```

Dependencies are declared in `gradle/libs.versions.toml` and locked in `gradle.lockfile`.

SonarQube Cloud project key: `SonarSource_sonarqube-mcp-server`.

## Code Style

- Follow [SonarSource/sonar-developer-toolset](https://github.com/SonarSource/sonar-developer-toolset#code-style).
- Every `.java` file must include the SSAL license header from `HEADER`.
- Commits and PRs should start with the Jira ticket ID when available (e.g. `MCP-1234`).

## Project Layout

```
src/main/java/org/sonarsource/sonarqube/mcp/
├── tools/           # MCP tool implementations, grouped by domain
├── serverapi/       # SonarQube REST API clients (*Api) and response records
├── transport/       # Stdio and HTTP (Streamable HTTP) MCP transports
├── slcore/          # SLCORE backend for local code analysis
├── configuration/   # Launch configuration from environment variables
├── client/          # Proxied MCP server loading (Context Augmentation)
├── authentication/  # HTTP transport authentication
├── bridge/          # SonarQube for IDE integration
└── SonarQubeMcpServer.java   # startup, tool registration, ServerApiProvider

src/test/java/.../harness/      # SonarQubeMcpServerTestHarness, WireMock, MCP test client
its/                            # Docker-based integration tests (Testcontainers)
docs/                           # Architecture and design documents
```

On startup, REST-based tools are registered immediately; SLCORE analyzer plugins download in the background and the backend restarts when ready (`docs/tool-loading.md`).

## SonarQube Cloud vs Server

The server connects to **SonarQube Cloud** when `SONARQUBE_ORG` is set, otherwise to **SonarQube Server** via `SONARQUBE_URL`. Many tools work on both; some are environment-specific:

| Area                            | Cloud                       | Server                       |
|---------------------------------|-----------------------------|------------------------------|
| System tools (`system` toolset) | Not available               | Available                    |
| Enterprises                     | Available                   | Not available                |
| Dependency risks (SCA)          | Org entitlement             | Feature flag + version check |
| Advanced analysis (A3S)         | Org entitlement             | Not available                |
| Context Augmentation (CAG)      | Org entitlement, stdio only | Proxied binary               |
| Agentic readiness               | Org feature flag            | Not available                |

When adding or changing tools, check whether behavior or registration must differ between Cloud and Server. Look at existing tools in the same domain for the established pattern (constructor flags, conditional registration in `SonarQubeMcpServer`, separate tool definitions).

## Adding an MCP Tool

**Schema bloat is a first-class concern.** MCP clients load the full tool list (names, descriptions, input schemas, output schemas) into the agent's context. Every tool description, parameter description, output field description, and title adds tokens on every session. Write the minimum text that still disambiguates usage — prefer one short sentence over a paragraph, and omit parameters that callers rarely need.

1. Create `FooTool.java` extending `Tool` in `tools/<domain>/`.
2. Add `package-info.java` if creating a new package.
3. Define `public static final String TOOL_NAME = "foo_action"` — snake_case, max 64 chars, MCP SEP-986 charset (`ToolNameValidator`).
4. Build the input schema with `SchemaToolBuilder.forOutput(FooToolResponse.class)`:
   - `.setTitle()` and `.setDescription()` — one or two short sentences: what the tool does and when to use it. No tutorials, examples, or repeated parameter detail (put that in parameter descriptions only).
   - Parameter descriptions (`.addStringProperty()`, etc.) — same rule: shortest text that clarifies format, valid values, or which other tool to call first.
   - `.addProjectKeyProperty()` when a project key is needed (respects `SONARQUBE_PROJECT_KEY` config).
   - `.addBranchAndPullRequestProperties()` when branch/PR-aware; use `BranchPullRequestContext` in `execute()` for validation.
   - `.setReadOnlyHint()` for tools that do not mutate SonarQube state.
   - Do not add optional parameters "for completeness" if they are rarely used — fewer parameters means a smaller schema.
5. Create `FooToolResponse` as a Java `record` with `@JsonPropertyDescription` on every field (drives the output JSON schema). See **Response field descriptions** below.
6. Implement `execute()` — call SonarQube via `ServerApiProvider.get()`, return `Tool.Result.success(response)`.
7. Assign a `ToolCategory` (controls `SONARQUBE_TOOLSETS` filtering; `projects` is always enabled). See **Tool categories** below.
8. Register in `SonarQubeMcpServer` — `loadBackendIndependentTools()` for REST tools, or the backend-dependent section for analysis tools.
9. Override `isEnabledFor()` only when per-request feature gating is needed.
10. Add `FooToolTests.java` (see Testing below).

Reference implementation: `tools/branches/ListBranchesTool.java`.

### Tool categories

Each tool belongs to exactly one `ToolCategory` (defined in `tools/ToolCategory.java`). The enum constant is passed to the `Tool` constructor; its `key` string is what users set in `SONARQUBE_TOOLSETS`.

Pick the category that matches the tool's domain. Only add a new enum value when no existing category fits — that also requires updating `ToolCategory`, default toolsets, and the README toolset table.

### Response field descriptions

`@JsonPropertyDescription` text becomes the `description` in the tool's output JSON schema — it is loaded into agent context alongside input schemas. **Keep every description as short as possible.**

Conventions in this repo:

- **Short phrase**, not a paragraph — typically 3–15 words; cut anything that does not help interpret or act on the value.
- **State what the field is**, and **how to use it** only when non-obvious (e.g. valid values, which other tool consumes it).
- **Mention environment differences** only when values differ between Cloud and Server.
- **Do not repeat the field name** — `"Project key"` for `projectKey`, not `"The projectKey field"`.
- **No examples or instructions in schema text** — if agents need a workflow, that belongs in the tool's `.setDescription()`, still kept brief.

```java
// Good — concise, actionable
@JsonPropertyDescription("Branch name that can be used with other tools as the branch parameter") String name,
@JsonPropertyDescription("Branch type in SonarQube (LONG on SonarQube Cloud, BRANCH on SonarQube Server)") BranchType type,

// Avoid — vague or redundant
@JsonPropertyDescription("The name") String name,
@JsonPropertyDescription("This field contains the branch type value") BranchType type,
```

Nested record fields (array items, sub-objects) each need their own `@JsonPropertyDescription`. Unit tests validate the generated schema via `assertSchemaEquals` — keep descriptions aligned with what the test expects.

### Server API Client

When a tool needs a new SonarQube endpoint:

1. Add `*Api.java` under `serverapi/<domain>/` with a `public static final String ..._PATH`.
2. Build URLs with `UrlBuilder`; parse responses with Gson into `response/` records.
3. Expose the API through the `ServerApi` facade.
4. Use try-with-resources on `helper.get(url)` / `helper.post(url, body)` responses.
5. Let `ServerApiHelper` exceptions (`UnauthorizedException`, `ForbiddenException`, etc.) propagate — `ToolExecutor` maps them to agent-friendly errors.

## Testing

Unit tests exercise the full MCP stack (server + client) against a WireMock SonarQube instance.

```java
@SonarQubeMcpServerTest
void it_should_list_branches(SonarQubeMcpServerTestHarness harness) {
  harness.getMockSonarQubeServer().stubFor(
    get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withResponseBody(Body.fromJsonBytes(...))));
  var result = harness.newClient().callTool(
    ListBranchesTool.TOOL_NAME, Map.of("projectKey", "my_project"));
  assertResultEquals(result, """{ "projectKey": "my_project", ... }""");
}
```

Conventions:

- Annotate test classes/methods with `@SonarQubeMcpServerTest`; inject `SonarQubeMcpServerTestHarness`.
- Name tests `it_should_<behavior>`.
- For each tool, test at minimum: output schema (`assertSchemaEquals`), annotations (`readOnlyHint`, `openWorldHint`), happy path, and relevant error paths (403, 500, empty results).
- Test Cloud vs Server differences when the tool behaves differently.
- `TELEMETRY_DISABLED=true` is set automatically by the harness.

Integration tests in `its/` use Testcontainers and are not run by `./gradlew test` or `./gradlew build`. Run a single class or method the same way as unit tests, on the `:its:integrationTest` task:

```bash
./gradlew :its:integrationTest --tests "org.sonarsource.sonarqube.mcp.its.HttpTransportITest"
./gradlew :its:integrationTest --tests "org.sonarsource.sonarqube.mcp.its.StdioTransportITest.should_start_server_with_stdio_transport"
```

## Key Docs

| Document                                   | Topic                                             |
|--------------------------------------------|---------------------------------------------------|
| `docs/tool-loading.md`                     | Startup phases and analyzer synchronization       |
| `docs/stdio-transport-architecture.md`     | Stdio MCP transport                               |
| `docs/http-authentication-architecture.md` | HTTP transport and authentication                 |
| `docs/proxied-mcp-servers.md`              | Proxied MCP servers (Context Augmentation)        |
| `docs/contributing.md`                     | Contribution policy                               |
| `README.md`                                | End-user setup, configuration, and tool reference |
