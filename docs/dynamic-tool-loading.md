# Dynamic Tool Loading

## The Problem

The SonarQube MCP Server needs to perform several initialization steps before tools can be used:
- Check SonarQube server version compatibility
- Download and synchronize analyzer plugins (can take a minute)
- Initialize the SonarLint backend with those analyzers
- Load and register all tools

In a traditional approach, the server blocks during all these steps, making users wait a long time before any functionality is available. The slow operations (plugin downloads, backend initialization) prevent even simple tools from being used.

## The Solution

The server starts immediately and loads tools in two phases:

**Phase 1: Load Backend-Independent Tools First**
- These tools only need the REST API to function
- No analyzers or backend initialization required
- Most of the functionality becomes available very quickly

**Phase 2: Load Backend-Dependent Tools After Heavy Initialization**
- These tools require the SonarLint backend (code analysis)
- Loaded after plugin synchronization and backend setup complete
- Only a small portion of total functionality

This approach provides the majority of tools almost immediately while the slow initialization happens in the background. Users can start working right away instead of staring at a loading screen.

## Architecture Flow

```
Server Startup (< 1 second)
    │
    ├─ Create basic services
    ├─ Setup transport (HTTP/stdio)
    └─ Start MCP server (0 tools initially)
    │
    ▼
Background Thread Starts
    │
    ├─ Check SonarQube version
    │
    ▼
┌──────────────────────────────────────┐
│ Phase 1: Independent Tools           │
│                                      │
│ ✅ Platform-specific tools           │
│ ✅ Common tools                      │
│                                      │
│ → Tools registered                   │
│ → Client notification sent           │
│ → Users can start working!           │
└──────────────────────────────────────┘
    │
    ▼
Heavy Operations (takes time)
    │
    ├─ Synchronize analyzer plugins
    ├─ Download missing analyzers
    ├─ Initialize SonarLint backend
    └─ Load analyzers
    │
    ▼
┌──────────────────────────────────────┐
│ Phase 2: Dependent Tools             │
│                                      │
│ ✅ Analysis tools                    │
│ ✅ Backend-dependent tools           │
│                                      │
│ → Tools registered                   │
│ → Client notification sent           │
│ → Full functionality available       │
└──────────────────────────────────────┘
    │
    ▼
Initialization Complete
```

## Client Notifications

The MCP protocol's `tools/list_changed` notification mechanism ensures clients stay synchronized:

- **Client connects before Phase 1:** Receives initial empty list, then notification when tools are added
- **Client connects after Phase 1:** Receives tools immediately during handshake
- **Client working during Phase 2:** Receives notification when analysis tools become available

The server attempts to notify connected clients after each phase. If no client is connected yet, notifications are silently ignored - the client will receive the full tool list during its initialization handshake.

## Tool Classification

When adding a new tool, determine which phase it belongs to:

**Backend-Independent (Phase 1)** - Add to `loadBackendIndependentTools()`
- Only requires `ServerApi` (REST API wrapper)
- No code analysis needed
- Examples: REST API wrappers, data retrieval, configuration management

**Backend-Dependent (Phase 2)** - Add to `loadBackendDependentTools()`
- Requires SonarLint backend
- Needs analyzer plugins
- Examples: any tool performing code analysis or requiring language analyzers

## Error Handling

The architecture provides graceful degradation:

- **Phase 1 fails:** No tools available (same as traditional approach)
- **Phase 2 fails:** Independent tools continue working, only backend-dependent features become unavailable
- **Notification fails:** Silently ignored, client receives tools during handshake

Even if plugin downloads fail or the backend cannot initialize, users can still access the majority of functionality.

## Testing

The test harness calls `server.waitForInitialization()` to ensure both phases complete before tests run. This guarantees tests always see the complete tool set and aren't affected by timing variations.

Additionally, the harness uses Awaitility to wait for the client transport to be ready, eliminating race conditions where tool notifications might fail because the client hasn't fully established its connection yet.

## Benefits

- **Faster time-to-first-tool:** Users can start working almost immediately
- **Better user experience:** No long waiting period for basic functionality  
- **Fault tolerance:** Backend failures don't break everything
- **Progressive enhancement:** Functionality appears as it becomes ready
- **Protocol compliant:** Uses standard MCP notification mechanism

---

**Related Files:**
- `src/main/java/org/sonarsource/sonarqube/mcp/SonarQubeMcpServer.java` - Main implementation
- `src/main/java/org/sonarsource/sonarqube/mcp/tools/ToolExecutor.java` - Waits for initialization
- `src/test/java/org/sonarsource/sonarqube/mcp/harness/SonarQubeMcpServerTestHarness.java` - Test synchronization

