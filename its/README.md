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
│           └── sonar-code-context-mcp    # Alpine Linux binary
```

## Architecture

Tests use [Testcontainers](https://www.testcontainers.org/) to run the server and proxied server binary in Alpine Linux containers.

The `sonar-code-context-mcp` binary is compiled for Alpine Linux (musl libc) and cannot run directly on most host systems.

## Test Container Dependencies

The following dependencies are automatically installed in test containers to match the production Dockerfile:
- **git**: Required by proxied servers
- **nodejs/npm**: Runtime dependencies
- **wget**: HTTP testing utility
