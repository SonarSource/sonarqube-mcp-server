# Development

Use JDK 21 and the Gradle wrapper. Docker is required for integration tests.
Internal contributors follow the [contribution policy](../contributing.md) and [PR template](../PULL_REQUEST_TEMPLATE.md).

## Build and verify

Run commands from the repository root:

```bash
./gradlew test --tests "org.sonarsource.sonarqube.mcp.tools.branches.ListBranchesToolTests"
./gradlew spotlessCheck
./gradlew clean build
./gradlew :its:integrationTest --tests "org.sonarsource.sonarqube.mcp.its.StdioTransportITest"
```

Use focused tests while iterating. Before handoff, run the affected suites; use the full build for changes to shared infrastructure, dependencies, or packaging. Root tests/build do not execute the Docker integration suite. Documentation-only changes need link/content checks and `git diff --check`, not a Java rebuild.

Spotless currently enforces license headers. It does not replace review against the [Java style guide](https://github.com/SonarSource/sonar-developer-toolset#code-style).
Follow any installed Vortex analysis protocol; report actual checks and skipped prerequisites.

## Dependencies

Versions live in `gradle/libs.versions.toml`. Regenerate the affected lockfiles with Artifactory credentials configured locally; never copy credentials into the repository:

```bash
./gradlew :dependencies --write-locks
./gradlew :its:dependencies --write-locks
```

## Preparing a PR

Prefer one independently testable behavior per PR. The 400-added-line target includes tests and documentation; line wrapping or moving code to another file does not make the change smaller. When a safe split is impossible, explain why and keep the diff focused.

A PR description states the problem, resulting behavior, relevant compatibility changes, validation, and known limitations. Cite the public API contract and release evidence when introducing an endpoint or changing availability. List dependencies for stacked PRs; compare each against its intended base.

Review the final diff after squashing your fixups. Re-run affected checks after conflict resolution or behavioral changes. Publishing requires task authorization; use `--force-with-lease` if an authorized update requires replacing your own previously pushed history.

## Design references

- [Adding or changing MCP tools](mcp-tools.md)
- [Instruction maintenance](agent-instructions.md)
- [Tool loading and analyzer synchronization](../tool-loading.md)
- [Stdio transport](../stdio-transport-architecture.md)
- [HTTP authentication and request isolation](../http-authentication-architecture.md)
- [Proxied MCP servers](../proxied-mcp-servers.md)
- [Architecture workflows and recovery](../architecture.md)
