# Integration Tests (ITS)

Integration tests for the SonarQube MCP Server.

## Running Tests

```bash
# From project root
./gradlew :its:integrationTest
```

**Note**: Integration tests are NOT run by default in `./gradlew test` or `./gradlew build`.

## Prerequisites

- Docker installed and running
- Java 21

## Test Structure

```
its/
├── build.gradle.kts
├── src/test/
│   ├── java/org/sonarsource/sonarqube/mcp/its/
│   │   ├── HttpTransportITest.java      # HTTP transport tests
│   │   ├── StdioTransportITest.java     # STDIO transport tests
│   │   └── ...
│   └── resources/
│       ├── proxied-mcp-servers-its.json
│       └── binaries/
│           └── sonar-context-augmentation    # Alpine Linux binary (Rust, musl)
```

## Architecture

Tests use [Testcontainers](https://www.testcontainers.org/) to run the server and proxied server binary in Alpine Linux containers.

The `sonar-context-augmentation` binary is compiled for Alpine Linux (musl libc) and cannot run directly on most host systems.

## Test Container Dependencies

The following dependencies are automatically installed in test containers to match the production Dockerfile:
- **nodejs**: Runtime dependencies
- **wget**: HTTP testing utility

## HTTP authentication against Staging

`HttpTransportITest` starts the current application JAR in two containers using
`McpServerTestContainers`: one with legacy authentication and one with OAuth configured.
The tests call the containers' mapped HTTP ports; their Cloud API calls go to Staging.
The existing HTTP CI job runs this class against the JAR built by the PR.

Set `SONARCLOUD_IT_TOKEN` to the existing Staging integration-test user token, supplied
through your secret manager or environment. The user must belong to `sonarlint-it`,
which must contain at least one project. No additional OAuth credentials are required.

```bash
./gradlew :its:integrationTest --tests 'org.sonarsource.sonarqube.mcp.its.HttpTransportITest'
```

The tests initialize an MCP client, list read-only tools, and read the user's organizations
and projects through both `Authorization: Bearer` and `SONARQUBE_TOKEN`, with and without
OAuth configuration. They verify that missing credentials receive HTTP 401 and that invalid
legacy tokens fail a protected Cloud read. Container-wide tokens are cleared so requests
must use the supplied credentials.

OAuth discovery checks the container's unauthenticated Bearer challenge and local metadata
endpoint, including the canonical Staging resource, issuer, scopes, and bearer method.
The OAuth container uses placeholder exchange-client credentials because discovery and
legacy requests do not use token exchange. These tests do not validate live OAuth login,
JWT verification, or token exchange; those flows have separate mocked unit coverage.
All live Cloud requests in this class are reads against Staging.
