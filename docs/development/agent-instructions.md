# Maintaining agent instructions

Use ordinary `AGENTS.md` files as the canonical guidance, following the same structure as `sonarqube-cli`.

## Organization

- The root contains essential workflow rules, verification entry points, and explicit routes. Scoped files contain only durable rules that differ in that area.
- Read applicable routes explicitly; nested instruction loading varies between clients. Source changes that add tests must also follow test guidance.
- Keep one canonical rule per concern. Link across scopes rather than duplicating prose in `.cursor/rules`, `CLAUDE.md`, or other client adapters. Add an adapter only for a demonstrated client need.
- Keep design rationale, examples, build recipes, and recovery details in `docs/`. Do not turn instruction files into feature inventories, ticket histories, or implementation summaries.
- Update rules when existing guidance becomes wrong, a durable convention changes, or a recurring mistake needs prevention. Ordinary feature work needs no instruction update. Prefer replacing stale guidance over appending.

## Size and review

- Root: aim for at most 50 lines, 700 words, and 6 KiB of maintained guidance.
- Each scoped file: at most 30 lines, 350 words, and 3 KiB.
- Check words and UTF-8 bytes as well as lines; long paragraphs are not a workaround.
- Preserve tool-managed blocks and markers exactly. They are excluded from these prose budgets; do not copy them into every scope.
- Explain necessary budget exceptions in the PR. Do not add a new scope if a link or correction to an existing rule is enough.

Before handoff, verify links, root routes, scope inheritance, and agreement with current code/tests. Remove contradictions and duplicated rules. No instruction-specific CI system or periodic maintenance task is required.
