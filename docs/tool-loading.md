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
- Load eligible tools and register those accepted by the configured toolsets and read-only mode
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
- `analyze_file_list` - Uses IDE bridge (if available); categorized as `ide`, selected by either `ide` or the legacy `analysis` selector
- Analysis works once analyzers are downloaded

Cloud stdio registers either Vortex analysis (when the workspace is mounted and at least one accessible organization is entitled) or local analysis, preserving an explicitly configured IDE bridge. `SONARQUBE_FORCE_LOCAL_ANALYSIS=true` selects snippet analysis even for entitled organizations in Cloud stdio only. Confirmed entitlements are cached for the session; the advanced tool still validates the selected organization.

Cloud and Server HTTP/HTTPS register `analyze_code_snippet` and download analyzers when a startup `SONARQUBE_TOKEN` is provided and the `analysis` toolset is enabled. The startup token is required for analyzer initialization; Cloud plugin downloads remain anonymous, while Server downloads use the startup token. Analysis calls retrieve quality profiles and active rules with the caller’s credentials and, on Cloud, the organization selected for the request. No Vortex entitlement is required. Without a startup token or with the `analysis` toolset disabled, snippet analysis and analyzer downloads are unavailable. `SONARQUBE_FORCE_LOCAL_ANALYSIS` cannot bypass the startup token requirement.

## Categories and toolset selection

Each tool has one canonical category. Startup and HTTP requests use the same category/read-only filter: `analysis` selects snippet analysis, IDE, and Vortex categories; the legacy `cag` selector selects Vortex only. Other selectors match their own category. Projects remain available subject to read-only and authorization checks.

HTTP headers filter the already registered inventory, so compatible selector names work in either direction without enabling absent tools. OAuth scopes and per-request eligibility checks apply separately. Telemetry records the raw selected keys that include the tool's category; these keys do not authorize tools.

## Accepted Tool Inventory

Eligible tools pass through `ToolRegistrar` once during synchronous startup. It applies the shared toolset and read-only matching rules;
existing loaders retain their deployment, version, entitlement, and feature checks. The server keeps one immutable accepted inventory for both
transports, HTTP request filtering, logging, and analyzer startup. HTTP request preferences can further narrow this inventory.

Architecture and agentic readiness instructions are included only when their group registers at least one tool, including read-only subsets.
Runtime instructions from connected proxied servers are forwarded independently, preserving each provider's guidance.
The analyzer download notice and background downloads are enabled only when `analyze_code_snippet` is actually registered.

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
