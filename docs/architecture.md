# Architecture workflows and API behavior

[Tool reference and setup](../README.md#architecture). This document contains the detailed output, validation and recovery behavior for Architecture workflows.

> **Pending feature documentation:** This section describes the combined target of the PRs below. An individual feature branch does not provide all of it. Until the relevant implementation lands, use the tools and parameters advertised by that build. Before these PRs land, `master` exposes current architecture only on Server, uses `intended-architectures`, rejects an empty intended model and allows directive pages up to 1000.

| Pending capability | Implementation PR |
| --- | --- |
| Server intended architecture and directives | [#678](https://github.com/SonarSource/sonarqube-mcp-server/pull/678) |
| Findings and their filters | [#680](https://github.com/SonarSource/sonarqube-mcp-server/pull/680) |
| Focused `from` / `to` dependency pagination | [#691](https://github.com/SonarSource/sonarqube-mcp-server/pull/691) |
| Response-contract compatibility hints | [#693](https://github.com/SonarSource/sonarqube-mcp-server/pull/693) |
| `project-intended-architectures` and empty-model DELETE | [#694](https://github.com/SonarSource/sonarqube-mcp-server/pull/694) |
| Directive summaries, 50-entry directive pages and validation/navigation improvements | [#695](https://github.com/SonarSource/sonarqube-mcp-server/pull/695) |
| Normalized directive identity, changed-field before/after previews and counts/confirmed-ID applies | [#703](https://github.com/SonarSource/sonarqube-mcp-server/pull/703) |

**Note: Add `architecture` to `SONARQUBE_TOOLSETS` to register these tools in stdio or HTTP mode. SonarQube Cloud requires a Team or Enterprise plan. On SonarQube Server, tools remain listed even when the Architecture feature or required public APIs are unavailable; those calls fail when invoked. Intended and directive operations need the public `/api/v2/components/projects` lookup and public Architecture APIs.**

- **architecture_get** - Read current dependencies, intended groups and constraints, project directives, or deviations, flaws and smells with their details. Findings are read from the latest main-branch analysis. Current views show at most 150 nodes; narrow the view with `component` or `language`. Omitted siblings may remain undiscoverable, so a capped view is not a complete graph export. `omittedNodes` counts nodes excluded by limits within the requested component/depth, not deeper descendants. Every returned node includes `descendants` and `truncated` navigation metadata; the five `drillDown` entries are suggestions. Selecting a leaf returns an explanatory message.

  Directive pages return `view: "summary"`: all matching page entries and their exact operation keys, origin and IDs, without descriptions. `descriptionOmitted` indicates whether a description is stored; use `directiveId` for the complete record. Summaries are not full exports. The default remains 50; pages are limited to 50 entries. Requests above 50 fail instead of being silently clamped. When reducing `pageSize`, restart at `pageIndex: 1` and continue paging. Backend totals are unchanged. This limit applies only to MCP list reads; write preflight and CLI pagination are unchanged. Backend pages still contain full records; this reduces tool output, not API transfer. Long keys and large pages can still produce large responses.

  For a focused dependency query, supply both `from` and `to` with exact current keys, without `component`. The query includes descendants and preserves direction. `depth` defaults to 1 (direct children, retaining leaves); use a returned pair or increase depth for detail. Matching leaf-level dependencies are aggregated before pagination, independent of the overview node cap. Ancestor/descendant aggregate rows are omitted; increase depth to inspect internal dependencies. Each page returns dependency keys/weights, `paging` metadata including the total, and `revision`; compare revisions before combining pages and restart if changed. Specify `language` if the pair matches multiple graphs. Container-level relationships overlapping both selected subtrees are rejected in this mode. Unrelated relationships are ignored. Matching graphs reject duplicate node IDs or ambiguous keys because complete dependency pages require unambiguous edge identities; the overview is best-effort. Counts limit rows, not bytes or tokens.

  If an Architecture response does not match the supported public API contract, the error asks you to check MCP/server compatibility. Authentication, permissions, missing resources, server errors and empty results do not establish a version incompatibility. No release checks or additional requests are made.

  Current graph reads are limited to two concurrent calls per tool instance. Additional calls wait up to five seconds for capacity before returning a busy error. Each accepted call still downloads and processes the full selected graph.

  Parameters:
  - `target` - `current`, `intended`, `directives` or `findings` - _Required Enum_
  - `projectKey` - The project key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `organization` - SonarQube Cloud organization key when none is configured; required for `current` and `findings` - _Optional String_
  - `component` - Exact returned current node key (Java: `module:package`) or intended group key (labels joined by `/`); no wildcards; top level when omitted - _String_
  - `depth` - Levels below the start (default: 1 for `current`, all for `intended`) - _Number_
  - `language` - `java`, `cs`, `js`, `ts` or `py`; current/intended/findings only - _String_
  - `directiveId` - Directives only: full record, including description, by UUID; cannot be combined with paging - _String_
  - `category` - Findings only: `deviation`, `flaw` or `smell` - _Enum_
  - `type` - Findings only: `structuralDeviation`, `relationshipDeviation`, `tangle`, `oversizedComponent`, `weakTangle` or `splitResponsibility` - _Enum_
  - `from` / `to` - Current only: exact source/destination keys including descendants; supply both without `component` - _String_
  - `pageIndex` - Directives, findings or current from/to queries; starts at 1, default 1 - _Number_
  - `pageSize` - Current from/to: default/maximum 20; directives: default/maximum 50; findings: default 50, maximum 1000, or 0 for count only - _Number_

  Findings filters combine with AND. Each row contains `type`, `category`, `language`, `details` and `files`; no finding ID or detail lookup is available. Empty results mean no findings are visible, rather than proof that a project is healthy. Both Cloud and Server use organization and main-branch context, without the Server project UUID lookup used by intended architecture and directives.

- **architecture_set** - Replace the whole intended model or create, update and delete explicit project directives. For intended models, start from an unfiltered `architecture_get` response. Use `dryRun: true` to review changes. Hidden when `SONARQUBE_READ_ONLY=true`. OAuth requires both read and write scopes.

  Intended reads and writes use the public `project-intended-architectures` API. Preview an empty complete model before applying its DELETE.

  **Directives:** Supply a JSON object with optional `create`, `update` and `delete` arrays. Create records contain directive fields, update records contain `{id, changed fields}`, and delete records contain only `{id}`. Unspecified update fields and unmentioned directives are retained. Creation skips duplicate operations. Preview with `dryRun=true` and apply only after confirmation. The whole batch is validated before writing; conflicting IDs are rejected. Writes are individual, not atomic. A partial or uncertain failure reports confirmed IDs and stops without retry or rollback. Duplicate checks and preflight reads can race concurrent edits. Optional fields cannot be cleared with null; the API has no conditional writes.

  Directive previews return proposed creates, changed values in `before` and `after` maps for updates (including an explicit null for a previously unset field), and delete targets without stored descriptions. Apply responses return counts and confirmed IDs without repeating the preview or description payloads. Unchanged fields remain on the server; no client replacement is inferred from summaries. New descriptions in create/update previews are shown in full. This reduces repeated content, but does not impose a byte limit on inputs or results.

  To change one description, read its `directiveId` if needed, then pass `model: "{\"update\":[{\"id\":\"<directive UUID>\",\"description\":\"New description\"}]}"` with `target: "directives"` and `dryRun: true`. After confirmation, send the same change file with `dryRun: false`. Other fields and directives remain untouched.

  Directive fields: `type` (`REMOVE_EDGE`, `RENAME_NODE`, `MOVE_NODE`), `ecosystem`, `graphType` (`file_graph`, `namespace_graph`), `fromNodeKey`, `toNodeKey`; optional `graphPerspective` (defaults to `default_perspective`) and `description`. Creation sets origin to `sonarqube-mcp-server`; updates preserve the stored origin. Identity comparisons normalize equivalent default perspectives without rewriting supplied values. For `MOVE_NODE`, `toNodeKey` is an existing destination container; for `RENAME_NODE`, it is the new complete key.

  **Validation:** Exact constraint endpoints must name unambiguous groups in the same perspective; self-references are rejected. Wildcard selectors and imported descendants retain their public semantics. A changed model is also checked against available discovery data: exact container patterns and skipped physical parents produce warnings, without rewriting patterns or moving groups. Failed or missing discovery is reported as unchecked, not as a missing match. Exact leaf patterns and logical groupings remain valid.

  For physical models, read bounded current slices first and keep intermediate parents. For example, in a GCToolkit-style layout under `gctoolkit-parser`, nest `jvm` and `unified` inside `com.microsoft.gctoolkit.parser` and use `gctoolkit-parser:com.microsoft.gctoolkit.parser.**` for that subtree. Follow a single-child artifact chain to its branching point. Keep exact patterns for leaves and preserve discovery-binding patterns when moving or renaming groups. Do not infer physical structure from labels alone or replace user-requested logical groupings.

  **Data-loss limitations:** Omitted perspectives and groups are removed. Perspective labels, descriptions and imported pattern links are preserved when supplied in the exported model. Imported patterns remain references: keep any exported child groups unchanged or omit them; derived children are excluded from writes, and imported contents cannot be edited here. Partial views are rejected. An empty complete model deletes the stored intended resource after preview. Supply the whole response to retain its revision check; concurrent edits can still race the check. Definite HTTP client rejections are relayed directly. Uncertain writes return a recovery export when the desired state cannot be verified; no automatic rollback is attempted.

  Parameters:
  - `target` - `intended` or `directives` - _Required Enum_
  - `projectKey` - The project key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `organization` - SonarQube Cloud organization key when none is configured - _Optional String_
  - `model` - Intended: whole unfiltered response or `models` array. Directives: `{create: [...], update: [...], delete: [...]}`, with fields listed above - _Required String_
  - `dryRun` - `true` to preview, `false` to apply - _Required Boolean_
  - `revision` - Intended only: exported revision; defaults to the whole response's revision. Bare arrays without it cannot detect changes since export - _String_
