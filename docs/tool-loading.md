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
- `analyze_file_list` - Uses IDE bridge (if available); belongs to both the `analysis` and `ide` toolsets
- Analysis works once analyzers are downloaded

Cloud stdio registers either Vortex analysis (when the workspace is mounted and at least one accessible organization is entitled) or local analysis, preserving an explicitly configured IDE bridge. `SONARQUBE_FORCE_LOCAL_ANALYSIS=true` selects snippet analysis even for entitled organizations in Cloud stdio only. Confirmed entitlements are cached for the session; the advanced tool still validates the selected organization.

Cloud and Server HTTP/HTTPS register `analyze_code_snippet` and download analyzers when a startup `SONARQUBE_TOKEN` is provided and the `analysis` toolset is enabled. The startup token is required for analyzer initialization; Cloud plugin downloads remain anonymous, while Server downloads use the startup token. Analysis calls retrieve quality profiles and active rules with the caller’s credentials and, on Cloud, the organization selected for the request. No Vortex entitlement is required. Without a startup token or with the `analysis` toolset disabled, snippet analysis and analyzer downloads are unavailable. `SONARQUBE_FORCE_LOCAL_ANALYSIS` cannot bypass the startup token requirement.

## Categories and toolset selection

`ToolDescriptor` declares each tool's categories, supported transports, and named availability requirement in one place. Tool constructors derive all toolset memberships from that declaration. IDE tools belong to `analysis` and `ide`; Vortex tools belong to `analysis`, `cag`, and `vortex`. Startup and HTTP requests use the same category/read-only filter: any selected membership matches. Projects remain available subject to read-only and authorization checks.

HTTP headers filter the already registered inventory. A tool can match different memberships at startup and per request; the two selected sets are never intersected. Headers cannot enable absent tools. OAuth scopes and per-request eligibility checks apply separately. Telemetry records the raw selected keys present in the tool's memberships; these keys do not authorize tools.

## Accepted Tool Inventory

`SonarQubeMcpServer` initializes services, resolves organizations and the Server version, then delegates tool loading:

- `ToolAvailability` resolves startup facts once: Architecture version support, Server SCA, SARA flags, and combined Vortex entitlement. Disabled optional toolsets skip their probes. HTTP SARA requires explicit server opt-in and retains backend entitlement enforcement on each call; HTTP SCA also defers caller checks to invocation. No startup entitlement or feature check substitutes for caller authorization.
- `AnalysisToolSelection` selects none, local, IDE, or Vortex after backend initialization. It constructs no tools. HTTP without a startup token has no analysis; Cloud force-local selects snippets while leaving Vortex proxy eligibility independent. An entitled Server Vortex installation without a workspace has no analysis tool and does not fall back to IDE or snippets.
- `ToolCatalog` contains the ordered, lazy tool factories paired with their canonical descriptors. It checks transport, selected categories, and resolved availability before construction. Architecture's factory runs once so get/set tools share their current-graph state. `ToolRegistrar` applies the shared category/read-only filter to those instances and returns one immutable accepted inventory.

The server keeps ownership of proxy connections and shutdown, and supplies discovered tools and provider instructions to the catalog. Both transports, HTTP request filtering, logging, and analyzer startup reuse the accepted tool instances. HTTP headers can narrow this inventory using the same category/read-only matching; they cannot trigger startup factories, select another analysis backend, or enable tools that were never registered. Verified OAuth scopes, tool invocation checks, and backend permissions still apply with the caller's credentials, without new discovery preflights.

Architecture and agentic readiness instructions are included only when their group registers at least one tool, including read-only subsets.
Runtime instructions from connected proxied servers are forwarded independently, preserving each provider's guidance.
The analyzer download notice and background downloads are enabled only when `analyze_code_snippet` is actually registered.

To add a tool, declare its descriptor, pass that descriptor to the tool constructor, and add one factory in `ToolCatalog`. Use an existing named availability requirement when possible. Keep caller-specific checks in the tool or backend; add a startup rule only when registration itself needs a new condition.

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
- `src/main/java/org/sonarsource/sonarqube/mcp/SonarQubeMcpServer.java` - Service and transport lifecycle
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolDescriptor.java` - Canonical tool metadata
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolAvailability.java` - Named startup requirements and resolved facts
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/AnalysisToolSelection.java` - Analysis backend selection
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolCatalog.java` - Lazy factories and accepted inventory
- `src/main/java/org/sonarsource/sonarqube/mcp/slcore/BackendService.java` - Backend restart logic
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolExecutor.java` - Waits for initialization
- `src/test/java/org/sonarsource/sonarqube/mcp/harness/SonarQubeMcpServerTestHarness.java` - Test synchronization
