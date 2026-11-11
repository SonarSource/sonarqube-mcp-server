# Architecture operations

Read [Architecture workflows](../../../../../../../../../docs/architecture.md) and [API guidance](../../serverapi/AGENTS.md) before changing contracts.

- Keep Architecture opt-in and preserve the Server minimum-version gate for both tools, including intended get/set. Change supported targets only against an explicit requirement and verified API support.
- Treat intended writes as full replacement. Reject partial exports; preserve round-trip metadata, imported-pattern references, revision checks, and the explicit clear guard.
- Validate labels and constraint endpoints at the scope defined by the public API; do not invent stricter uniqueness or relationship rules from a single fixture.
- Do not silently discard unknown write fields. Distinguish malformed known response data from explicitly handled future variants; neither means a clean or empty architecture.
- Preserve write preflight and recovery semantics. Report confirmed partial results; do not automatically retry or roll back ambiguous mutations.
- Findings and graph drill-down must retain the same filtering context and remain bounded. Truncated views must not be accepted as complete replacement models.
