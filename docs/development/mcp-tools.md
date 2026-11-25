# Adding or changing an MCP tool

Start from a tool in the same domain. [ListBranchesTool](../../src/main/java/org/sonarsource/sonarqube/mcp/tools/branches/ListBranchesTool.java) demonstrates deployment-specific schemas and typed responses; use it for structure, not as a reason to copy every description.

## Contract and availability

Establish the public API contract before implementing the client. Capture exact required/optional fields, numeric types and limits, enum values, pagination, and mutation semantics. A successful mocked response validates the client against that fixture; it does not establish backend support.

Cloud, Server, and Community Build can expose different capabilities. Availability may also depend on release version, edition, entitlement, feature flags, transport, and configuration. A draft Swagger change or snapshot build is provisional evidence. Keep unsupported operations out of the advertised schema and protect execution as well.

The startup gate applies to both HTTP and stdio. HTTP adds caller-specific permissions and filtering; it cannot expand the startup tool set. A new optional `ToolCategory` declares no default transports unless the task explicitly calls for a default change. Preserve unrelated tool behavior and avoid extra startup requests for disabled features.

## Implementation

1. Add the domain API client/DTOs through `ServerApi` when needed; use the shared transport and URL builder.
2. Add a `Tool` subclass under its domain, plus `package-info.java` for a new package. Use a snake_case `TOOL_NAME` of at most 64 characters, accepted by `ToolNameValidator`.
3. Build the schema with `ToolDefinitionBuilder`; reuse configured-project, organization, and branch/pull-request handling. Use `BranchPullRequestContext` where applicable.
4. Return a typed record through `Tool.Result.success`. Null fields are omitted; the payload is serialized once as JSON text. Tools do not advertise output schemas.
5. Declare a `private static final ToolAvailability REGISTRATION_AVAILABILITY` on the tool and pass it to `super` with the definition. Register the instance in `ToolCatalog` with `register(new FooTool(...))`; the catalog reads its availability. Use `isEnabledFor()` for caller-specific checks when needed, not as a substitute for known startup gates.
6. Add regression coverage through the MCP harness and update the user reference.

## Registration availability

Ordinary tools need only their categories:

```java
private static final ToolAvailability REGISTRATION_AVAILABILITY = ToolAvailability.inCategory(ToolCategory.ISSUES);
```

Both transports are supported by default. Use `.cloudOnly()`, `.serverOnly()`, or `.requiresIdeAnalysis()` for common startup restrictions. Other capabilities use `.requires(...)`:

```java
private static final ToolAvailability REGISTRATION_AVAILABILITY = ToolAvailability.inCategories(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX)
  .stdioOnly().requires(ToolCapabilities::vortexAnalysis);
```

`ToolCapabilitiesResolver` resolves shared startup decisions through the existing API clients and returns the capabilities together with the available IDE bridge client. `ToolAvailability` reads the immutable `ToolCapabilities` snapshot and performs no network calls. Add a capability here when a tool needs a new startup condition; keep the declaration on the tool and dependency construction in `ToolCatalog`.

The catalog checks each instance's `availability().isAvailable(...)` for startup transport and capability requirements. Use the `register(REGISTRATION_AVAILABILITY, () -> new FooTool(...))` overload only when construction requires dependencies that may be absent; it checks availability before invoking the factory. Make the constant public only when another package needs to check it before construction or proxy startup. The catalog constructor produces the supported tools and the enabled subset after category and read-only filtering. A match on any category enables a tool; `projects` remains always enabled. Default transports are declared beside each `ToolCategory` key: omitted transports mean opt-in, and agentic readiness defaults to stdio only. These are selection defaults, not transport support restrictions. Keep existing aliases and defaults intact.

Proxy startup and shutdown belong to `SonarQubeMcpServer`, which supplies discovered tools to the catalog and composes server instructions. Keep feature workflow text beside the feature, as in `StartAgenticReadinessAssessmentTool.INSTRUCTIONS`.

Startup registration and caller authorization are separate. Retain HTTP request filtering, OAuth scope enforcement, and execution-time entitlement checks for the selected organization. A startup entitlement cannot authorize an HTTP caller.

## Input and output budgets

MCP clients load the tool descriptions and input schemas into context. Every optional parameter and repeated explanation has a cost on every session. Keep descriptions short and expose only useful controls. Prefer an accurate small schema over an opaque JSON string; when a string is required, validate its full write contract before any mutation.

Schemas and runtime checks must agree. Integral pagination/depth must be declared as integers with the applicable bounds. Validate target-specific combinations and destructive input before backend writes. Do not silently discard unknown write fields or invent validation rules stronger than the documented API.

Page size alone does not bound output: one finding can contain thousands of files or graph nodes. Define limits for nested collections and large fields. Detail/recovery reads must respect the same budget, with explicit truncation, totals, and a usable continuation path. Preserve exact identifiers; do not shorten keys silently.

Do not present filtered or truncated data as a complete export. Preserve the scope and revision needed to continue reading safely. Distinguish a legitimately empty result from missing, malformed, unavailable, or skipped data.

## Validation evidence

Cover advertised schemas and actual MCP calls, not just direct Java method calls. Include valid contract examples, malformed responses, permission failures, boundary values, large nested payloads, and continuation after truncation where relevant.

For deployment changes, cover defaults and explicit opt-in, supported/unsupported versions, Cloud/Server differences, HTTP/stdio, and read-only behavior. Shared argument parsing or registration changes need tests outside the new domain too.

State what is verified by unit tests, integration tests, and any live backend checks. Unreleased API support remains a release dependency even when local fixtures and CI pass.
