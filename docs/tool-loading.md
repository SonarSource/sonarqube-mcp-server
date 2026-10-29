# Tool Loading and Analyzer Synchronization

## The Problem

The SonarQube MCP Server needs to perform several initialization steps:
- Check SonarQube server version compatibility
- Initialize the SonarLint backend
- Download and synchronize analyzer plugins (can take a minute)
- Load and register all tools

Downloading analyzers is slow and shouldn't block tool availability.

## The Solution

The server initializes the backend immediately with empty analyzers, loads all tools synchronously, then downloads analyzers in the background and restarts the backend with them.

**Phase 1: Immediate Startup (synchronous)**
- Initialize backend with empty analyzers
- Check IDE bridge availability (requires backend)
- Load and register ALL tools
- Start MCP server with full tool list

**Phase 2: Analyzer Download (background)**
- Download and synchronize analyzer plugins
- Restart backend with downloaded analyzers
- Code analysis tools become fully functional

This approach makes all tools available immediately. The slow analyzer download happens in the background without blocking users.

## Architecture Flow

```
Server Startup
    │
    ├─ Create basic services
    ├─ Check SonarQube version
    │
    ▼
┌──────────────────────────────────────┐
│ Phase 1: Immediate Initialization    │
│                                      │
│ ✅ Initialize backend (no analyzers) │
│ ✅ Check IDE bridge availability     │
│ ✅ Load ALL tools                    │
│                                      │
│ → Full tool list available           │
│ → Users can start working!           │
└──────────────────────────────────────┘
    │
    ▼
Start MCP Server (all tools registered)
    │
    ▼
Background Thread Starts
    │
    ├─ Synchronize analyzer plugins
    ├─ Download missing analyzers
    │
    ▼
┌──────────────────────────────────────┐
│ Phase 2: Backend Restart             │
│                                      │
│ ✅ Shutdown current backend          │
│ ✅ Restart with new analyzers        │
│                                      │
│ → Code analysis fully functional     │
└──────────────────────────────────────┘
    │
    ▼
Initialization Complete
```

## Tool Availability

All tools are registered at startup:

**REST API Tools** - Work immediately
- Project search, issue management, quality gates
- System health, metrics, rules
- No analyzer dependency

**Analysis Tools** - Available immediately, fully functional after Phase 2
- `analyze_code_snippet` - Analyzes code using SonarLint backend. **Deprecated** in favor of `analyze_file_list` and Vortex toolset, will be removed in a future release
- `analyze_file_list` - Uses IDE bridge (if available); also tagged under the `ide` toolset alongside `analysis`
- Analysis works once analyzers are downloaded

Cloud stdio registers either Vortex analysis (when the workspace is mounted and at least one accessible organization is entitled) or local analysis, preserving an explicitly configured IDE bridge. `SONARQUBE_FORCE_LOCAL_ANALYSIS=true` selects snippet analysis even for entitled organizations. Confirmed entitlements are cached for the session; the advanced tool still validates the selected organization.

Cloud HTTP/HTTPS registers no analysis tools and skips analyzer downloads. SonarQube Server HTTP/HTTPS retains local analysis and analyzer downloads when a startup token is provided.

### Declaring tool availability

Toolset activation and tool availability are evaluated separately:

- `ToolCategory` declares the transports on which a toolset is enabled by default. Categories without default transports are opt-in. An explicit `SONARQUBE_TOOLSETS` value replaces the default selection; `projects` remains enabled. HTTPS uses the HTTP defaults.
- `Tool.availabilityPolicy()` declares supported transports, deployments, and startup requirements. The default is `ToolAvailabilityPolicy.UNRESTRICTED`. Tools with the same restrictions share an immutable policy; `CLOUD_ONLY` and `SERVER_ONLY` cover simple deployment restrictions.
- Named startup requirements are resolved through `ToolStartupRequirements`, using the existing API clients. `ToolRegistrar` caches each result, including failure, for the server lifetime. A restart is required to retry a failed requirement or pick up a changed startup feature flag.

For a simple deployment restriction, declare it on the tool:

```java
@Override
public ToolAvailabilityPolicy availabilityPolicy() {
  return ToolAvailabilityPolicy.CLOUD_ONLY;
}
```

For a conditional feature, use a shared policy with a requirement for the relevant transport. All three agentic-readiness tools share a Cloud-only policy requiring `SARA_ENABLED` on STDIO. HTTP has no startup feature lookup: the toolset is opt-in and the backend enforces access on each call. On STDIO, the requirement checks the configured or uniquely detected organization; otherwise it accepts any accessible organization with the flag enabled. Individual lookup failures do not stop checks for other organizations.

Loaders submit tools to `ToolRegistrar`. The registrar checks category selection, read-only mode, transport/deployment support, then startup requirements. Rejected tools do not trigger requirement lookups. A group can supply instructions, which are included once if at least one tool is accepted. After loading, one immutable registered-tool list supplies both MCP transports, HTTP filtering, and the decision to download local analyzers.

HTTP request authorization remains separate from startup availability. `PerRequestToolFilteringHandler` applies client toolset/read-only filters, OAuth scopes, and `Tool.isEnabledFor()` to discovery and invocation. Client headers can only narrow the registered tools. Startup eligibility does not authorize a request; backend authorization still applies on each call.

Vortex entitlement orchestration, dependency-risk probing, IDE detection, and analysis fallback selection remain in their specialized loaders. Their resulting tools also pass through the registrar.

## Backend Restart

The `BackendService.restartWithAnalyzers()` method handles the transition:

1. If backend not initialized → Initialize with analyzers directly
2. If backend already running:
   - Shut down current backend gracefully
   - Close launcher
   - Reset state
   - Initialize new backend with analyzers

Error handling ensures restart proceeds even if shutdown fails.

## Error Handling

The architecture provides graceful degradation:

- **Backend initialization fails:** Server continues, analysis tools unavailable
- **Analyzer download fails:** Tools work, but analysis has limited language support
- **Backend restart fails:** Logs warning, continues with previous state

## Testing

The test harness calls `server.waitForInitialization()` to ensure both phases complete before tests run. This guarantees tests see the backend fully initialized with analyzers.

## Benefits

- **Instant tool availability:** All tools registered at startup
- **No blocking:** Analyzer download happens in background
- **Better UX:** Users can browse projects, issues, rules immediately
- **Fault tolerance:** Analyzer failures don't break REST API tools
- **IDE bridge check at startup:** Proper tool selection from the start

---

**Related Files:**
- `src/main/java/org/sonarsource/sonarqube/mcp/SonarQubeMcpServer.java` - Main implementation
- `src/main/java/org/sonarsource/sonarqube/mcp/slcore/BackendService.java` - Backend restart logic
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolExecutor.java` - Waits for initialization
- `src/test/java/org/sonarsource/sonarqube/mcp/harness/SonarQubeMcpServerTestHarness.java` - Test synchronization
