# Integration Tests (ITS)

Integration tests for the SonarQube MCP Server, validating end-to-end functionality for both HTTP and STDIO transports with external tool provider integration.

## Running Tests

```bash
# From project root
./gradlew :its:integrationTest

# With verbose output
./gradlew :its:integrationTest --info
```

**Note**: Integration tests are NOT run by default in `./gradlew test` or `./gradlew build`.

## Prerequisites

- Docker installed and running
- Java 21
- At least 4GB available disk space

## Test Structure

```
its/
├── build.gradle.kts
├── src/test/
│   ├── java/org/sonarsource/sonarqube/mcp/its/
│   │   ├── HttpTransportITest.java      # HTTP transport tests
│   │   ├── StdioTransportITest.java     # STDIO transport tests
│   │   └── ExternalToolProviderITest.java  # Binary validation (disabled)
│   └── resources/
│       ├── external-tool-providers-its.json
│       └── binaries/
│           └── sonar-code-context-mcp    # Alpine Linux binary
```

## What Gets Tested

- Server startup with HTTP and STDIO transports
- External provider configuration loading and validation
- Graceful degradation when external providers fail
- HTTP endpoint accessibility and security warnings
- Complete E2E flow in containerized environment

## Architecture

Tests use [Testcontainers](https://www.testcontainers.org/) to run the server and external provider binary in Alpine Linux containers, ensuring:
- Platform independence (Windows/Mac/Linux)
- Consistent environment matching production
- No local system pollution

The `sonar-code-context-mcp` binary is compiled for Alpine Linux (musl libc) and cannot run directly on most host systems.

## Test Container Dependencies

The following dependencies are automatically installed in test containers to match the production Dockerfile:
- **git**: Required by external provider
- **nodejs/npm**: Runtime dependencies
- **wget**: HTTP testing utility

## Troubleshooting

**Docker not available**: Install Docker Desktop or ensure Docker daemon is running

**Container startup slow**: Normal for first run (image download). Subsequent runs use cached images

**Binary permissions error**: Ensure binary has execute permissions:
```bash
chmod +x its/src/test/resources/binaries/sonar-code-context-mcp
```

## CI/CD

```bash
./gradlew :its:integrationTest --no-daemon
```
