# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Development Commands

### Build and Test
- `./gradlew clean build -x test` - Clean and build the project without running tests
- `./gradlew clean build` - Clean, build, and run all tests
- `./gradlew test` - Run all tests (includes test plugin preparation)
- `./gradlew jar` - Build the JAR file (created in `build/libs/`)
- `./gradlew jacocoTestReport` - Generate test coverage reports

### Development
- `./gradlew run` - Run the MCP server locally
- `./gradlew prepareTestPlugins` - Prepare SonarQube test plugins for testing

## Project Architecture

This is a **SonarQube MCP (Model Context Protocol) Server** that provides code quality and security analysis capabilities to AI agents. It's built with Java 21 and Gradle.

### Core Components

**Main Entry Point:**
- `SonarQubeMcpServer.java` - Main server class that handles MCP protocol communication

**Key Architecture Layers:**

1. **Transport Layer** (`transport/`)
   - `StdioServerTransportProvider.java` - Handles stdio-based MCP communication

2. **Tools Layer** (`tools/`)
   - Contains all MCP tools organized by domain (analysis, issues, measures, etc.)
   - Each tool implements specific SonarQube functionality exposed to AI agents
   - `ToolExecutor.java` - Coordinates tool execution

3. **Server API Layer** (`serverapi/`)
   - Abstracts SonarQube Web API calls
   - Organized by API domain (components, issues, measures, metrics, etc.)
   - `ServerApi.java` - Core API client functionality

4. **SLCore Integration** (`slcore/`)
   - `BackendService.java` - Integrates with SonarLint core for code analysis
   - `McpSonarLintRpcClient.java` - RPC client for SonarLint communication

5. **Configuration** (`configuration/`)
   - `McpServerLaunchConfiguration.java` - Server configuration management

6. **HTTP Client** (`http/`)
   - `HttpClientProvider.java` - HTTP client abstraction for SonarQube API calls

### Tool Categories

The server exposes these tool categories to AI agents:
- **Analysis** - Code snippet analysis using SonarQube analyzers
- **Issues** - Search and manage SonarQube issues
- **Measures** - Get component metrics and measures
- **Projects** - Search and manage SonarQube projects
- **Quality Gates** - Quality gate status and management
- **Rules** - SonarQube rule information
- **Sources** - Access source code and SCM information
- **System** - SonarQube server health and status (Server only)

### Build System

- Uses **Gradle** with Kotlin DSL (`build.gradle.kts`)
- Requires **Java 21**
- Fat JAR packaging with all dependencies included
- Test plugins are automatically prepared from SonarQube analyzers
- Supports both local builds and CI/CD via Artifactory

### Key Dependencies

- MCP Server SDK for protocol handling
- SonarLint Java client for code analysis
- Apache Commons for utilities
- JUnit 5 + Mockito + AssertJ for testing
- WireMock for HTTP API testing

### Environment Configuration

The server requires environment variables for SonarQube connectivity:
- `STORAGE_PATH` - Directory for MCP server files
- `SONARQUBE_TOKEN` - Authentication token
- `SONARQUBE_ORG` - Organization key (for SonarQube Cloud)
- `SONARQUBE_URL` - Server URL (for SonarQube Server)