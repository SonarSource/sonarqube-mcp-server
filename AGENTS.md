# SonarQube MCP Server

Java 21 / Gradle server exposing SonarQube Cloud and Server through MCP.

## Working and delivery

- Start new work from the latest intended base. Inspect the checkout first; preserve unrelated changes and use an isolated branch when needed.
- Keep one purpose per PR. Aim for **400 added lines or fewer**, including tests and docs. Split independently testable changes; explain an inseparable exception in the PR.
- Squash your WIP/fixup commits into one coherent commit per PR before handoff, unless requested otherwise. Do not rewrite collaborators' commits. Keep stacked PR dependencies explicit.
- Use `task/<initials>/<ticket>-<description>`, `feature/...`, or `bug/...` branches. Prefix commits and PR titles with the Jira key when available; omit it when none exists. Commit messages contain a title only.
- Push only when the task authorizes it. A request to open a PR authorizes pushing its branch, not merging or releasing.
- Read surrounding code and follow existing abstractions. Keep unrelated cleanup out of the change. Never commit credentials or put tokens, private source, or recovery exports in logs or telemetry.

## Verification

- Add regression coverage for code changes. Run the relevant Gradle tests and `./gradlew spotlessCheck`; use the [development guide](docs/development/README.md) for build, integration tests, and dependency locks.
- Fix new test or analysis failures; verify claims of pre-existing failures against the base.
- Inspect the final diff for scope, code style, schema size, and compatibility. Report checks actually run and any remaining limitations; green mocks do not prove a remote API is available.

## Read before editing the relevant area

Paths below are explicit routes: read the applicable files even when your agent does not automatically load nested instructions.

- Java and source conventions: [src/AGENTS.md](src/AGENTS.md). Production code, startup, configuration, auth, and transports: [src/main/AGENTS.md](src/main/AGENTS.md).
- MCP tools and schemas: [tools/AGENTS.md](src/main/java/org/sonarsource/sonarqube/mcp/tools/AGENTS.md). Architecture operations: [architecture/AGENTS.md](src/main/java/org/sonarsource/sonarqube/mcp/tools/architecture/AGENTS.md).
- SonarQube APIs and DTOs: [serverapi/AGENTS.md](src/main/java/org/sonarsource/sonarqube/mcp/serverapi/AGENTS.md).
- Unit tests and harness: [src/test/AGENTS.md](src/test/AGENTS.md). Docker integration tests: [its/AGENTS.md](its/AGENTS.md). Read test guidance when adding tests for production changes.
- Documentation: [docs/AGENTS.md](docs/AGENTS.md). Build and dependency changes: [development guide](docs/development/README.md).
- Instruction changes: [instruction policy](docs/development/agent-instructions.md). Keep one canonical rule per concern; ordinary feature work does not require adding rules.
