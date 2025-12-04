# SonarQube MCP Server: Multi-Squad Integration Architecture

## Specification Document

**Version:** 1.0  
**Date:** December 2024  
**Status:** Implemented  
**Owner:** IDE-XP Squad

---

## Executive Summary

The SonarQube MCP Server, maintained by IDE-XP, can now integrate MCP capabilities developed by other squads across SonarSource. This enables a **single, unified SonarQube MCP Server** that incorporates specialized functionality from different teams—without requiring those teams to modify the core server.

---

## Context

### Current Ownership Model

- **IDE-XP Squad** owns and maintains the SonarQube MCP Server
- The server exposes SonarQube capabilities (issues, quality gates, analysis, etc.) to AI assistants
- Other squads are developing MCP-related capabilities for their domains

### The Challenge

As more squads develop MCP functionality, we face a choice:

**Option A: Multiple Separate Servers**
```
AI Assistant connects to:
  ├── SonarQube MCP Server (IDE-XP)
  ├── Squad A's MCP Server
  ├── Squad B's MCP Server
  └── Squad C's MCP Server

Problems:
  • Users must configure multiple servers
  • Fragmented experience
  • Complex deployment
  • Inconsistent versioning
```

**Option B: Everyone Contributes to One Codebase**
```
All squads commit to the SonarQube MCP Server repo

Problems:
  • Merge conflicts
  • Tight coupling
  • Release coordination nightmares
  • Ownership confusion
```

**Option C: Plugin Architecture (What We Built)**
```
SonarQube MCP Server (IDE-XP)
  │
  └── Integrates at runtime:
        ├── Squad A's MCP component
        ├── Squad B's MCP component
        └── Squad C's MCP component

Benefits:
  • Single server for users
  • Squads maintain their own components
  • Clear ownership
  • Independent release cycles
```

---

## Architecture

### The Integration Model

```
┌─────────────────────────────────────────────────────┐
│                                                     │
│         SonarQube MCP Server                        │
│         (Owned by IDE-XP)                           │
│                                                     │
│  ┌────────────────────────────────────────────┐    │
│  │  Core SonarQube Tools                       │    │
│  │  • Issues, Quality Gates, Analysis, etc.   │    │
│  │  (Maintained by IDE-XP)                    │    │
│  └────────────────────────────────────────────┘    │
│                                                     │
│  ┌────────────────────────────────────────────┐    │
│  │  Integration Layer                          │    │
│  │  (Connects to other squad's components)    │    │
│  └──────────────────┬─────────────────────────┘    │
│                     │                               │
└─────────────────────┼───────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
        ▼             ▼             ▼
   ┌─────────┐   ┌─────────┐   ┌─────────┐
   │ Squad A │   │ Squad B │   │ Squad C │
   │   MCP   │   │   MCP   │   │   MCP   │
   │Component│   │Component│   │Component│
   └─────────┘   └─────────┘   └─────────┘

   Each squad owns    Runs as separate    Integrated into
   their component    process             unified experience
```

### How It Works

1. **IDE-XP** maintains the core SonarQube MCP Server
2. **Other squads** develop their MCP components as standalone servers
3. **At deployment**, IDE-XP configures which squad components to integrate
4. **At runtime**, the SonarQube MCP Server connects to each component and exposes their tools
5. **Users** see one unified set of tools from one server

---

## Squad Integration Process

### For Squads Developing MCP Components

```
1. Develop your MCP server following the MCP protocol
   │
2. Package it as a standalone executable
   │
3. Provide IDE-XP with:
   │   • How to run it (command + arguments)
   │   • What environment variables it needs
   │   • A short identifier for your tools
   │
4. IDE-XP adds your component to the deployment configuration
   │
5. Your tools appear in the unified SonarQube MCP Server
```

### What Squads Need to Provide

| Information | Description | Example |
|-------------|-------------|---------|
| **Identifier** | Short name for your component (used as tool prefix) | "codecontext" |
| **Command** | How to start your MCP server | "java -jar codecontext-mcp.jar" |
| **Arguments** | Any required command-line options | ["--config", "/etc/config.json"] |
| **Environment** | Required environment variables | {"API_KEY": "..."} |

### Tool Naming Convention

To avoid conflicts between squads, each component's tools are automatically prefixed:

| Squad | Original Tool Name | Exposed As |
|-------|-------------------|------------|
| Code Context Squad | get_context | **codecontext_get_context** |
| Code Context Squad | search_symbols | **codecontext_search_symbols** |
| Another Squad | analyze | **anothersquad_analyze** |

---

## Deployment Configuration

### Configuration Format

IDE-XP maintains the deployment configuration:

```json
[
  {
    "name": "codecontext",
    "command": "/opt/sonar-mcp/codecontext-server",
    "args": ["--mode", "production"],
    "env": {
      "CONFIG_PATH": "/etc/sonar-mcp/codecontext.conf"
    }
  },
  {
    "name": "anothersquad",
    "command": "java",
    "args": ["-jar", "/opt/sonar-mcp/another-squad.jar"],
    "env": {}
  }
]
```

### Environment Variable

The configuration is provided via: `SONARQUBE_EXTERNAL_SERVERS`

This can be:
- A path to the JSON configuration file
- The JSON directly as a string

---

## Responsibilities

### IDE-XP Squad (Server Owner)

| Responsibility | Description |
|----------------|-------------|
| Core server maintenance | Bug fixes, upgrades, releases |
| Integration configuration | Adding/removing squad components |
| Deployment | Managing the unified server deployment |
| User-facing documentation | How to use the unified server |
| Coordination | Working with squads on integration |

### Contributing Squads

| Responsibility | Description |
|----------------|-------------|
| Component development | Building and maintaining their MCP server |
| Component testing | Ensuring their server works correctly |
| Component packaging | Providing a deployable artifact |
| Integration spec | Documenting how to run their component |
| Component updates | Providing new versions to IDE-XP |

---

## User Experience

### What Users See

Users connect to **one** SonarQube MCP Server and see all tools:

```
Available Tools:
─────────────────────────────────────
Core SonarQube (IDE-XP):
  • analyze_code_snippet
  • search_sonar_issues_in_projects
  • get_project_quality_gate_status
  • list_quality_gates
  • show_rule
  • ... (all core tools)

Code Context (Code Context Squad):
  • codecontext_get_context
  • codecontext_search_symbols
  • codecontext_get_dependencies
  • ...

Another Component (Another Squad):
  • anothersquad_analyze
  • anothersquad_report
  • ...
─────────────────────────────────────
```

### Single Configuration

Users only need to configure one MCP server:

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "...",
      "env": {
        "SONARQUBE_TOKEN": "..."
      }
    }
  }
}
```

They automatically get access to all integrated squad capabilities.

---

## Runtime Behavior

### Startup Sequence

```
SonarQube MCP Server starts
         │
         ▼
Core SonarQube tools load immediately
         │
         ▼
Integration layer starts each squad component
         │
    ┌────┴────┬────────────┐
    │         │            │
    ▼         ▼            ▼
Squad A   Squad B     Squad C
starts    starts      starts
    │         │            │
    └────┬────┴────────────┘
         │
         ▼
All tools from all squads become available
         │
         ▼
Server ready with unified tool set
```

### Fault Isolation

Each squad's component runs as a separate process:

| Scenario | Behavior |
|----------|----------|
| Squad A's component crashes | Squad A's tools unavailable; everything else works |
| Squad B's component is slow | Only Squad B's tools are slow |
| Squad C's component won't start | Logged and skipped; server continues |

This means:
- **Core SonarQube tools are always available**
- One squad's issues don't break another squad's functionality
- Problems are isolated and easy to diagnose

---

## Benefits

### For Users

- **One server to configure** instead of many
- **Unified experience** across all SonarQube capabilities
- **Simpler setup** and maintenance

### For IDE-XP

- **Clean architecture** with clear boundaries
- **Easy to add/remove** squad components
- **No code changes needed** to integrate new components

### For Contributing Squads

- **Own your component** end-to-end
- **Independent development** and release cycles
- **No merge conflicts** with other squads
- **Standard integration** process

### For SonarSource

- **Faster innovation** with parallel development
- **Clear ownership** reduces confusion
- **Scalable model** as more squads contribute

---

## Limitations

| Limitation | Implication |
|------------|-------------|
| Components must be local | Can't integrate remote servers (yet) |
| Configuration at startup | Need restart to add new components |
| Sequential tool execution | One tool call at a time per component |

---

## Summary

### What We Built

An **integration architecture** that allows multiple squads to contribute MCP capabilities to a single, unified SonarQube MCP Server—without coupling their code or coordination overhead.

### The Model

```
┌──────────────────────────────────────────┐
│                                          │
│   SonarQube MCP Server                   │
│   (IDE-XP owns the integration point)    │
│                                          │
│   ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│   │  Core    │ │ Squad A  │ │ Squad B  │ │
│   │(IDE-XP)  │ │Component │ │Component │ │
│   └──────────┘ └──────────┘ └──────────┘ │
│                                          │
└──────────────────────────────────────────┘
                    │
                    ▼
            Single unified
            experience for
               users
```

### Key Takeaway

**Squads can innovate independently while users get a cohesive, single-server experience.**

---

*For technical integration details, contact IDE-XP squad.*
