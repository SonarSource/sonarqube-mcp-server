#!/usr/bin/env python3
# SonarQube MCP Server
# Copyright (C) SonarSource SA
# mailto:info AT sonarsource DOT com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

"""
Build-time script: generate GitHub release notes for the SonarQube MCP Server
via the Anthropic API. Driven by the `prepare-release-notes` job in
.github/workflows/prepare-release-notes.yml.

Can also be run locally for preview (no API call):

    python3 .github/scripts/generate-release-notes.py --tag 1.19.0 --dry-run

In CI (RELEASED_VERSION + CLAUDE_CODE_API_KEY set):

    python3 .github/scripts/generate-release-notes.py --out release-notes.md

The script:
1. Resolves the previous release tag with `git describe`.
2. Collects commit subjects between the previous tag and the released one
   (or HEAD when the release tag has not been pushed yet).
3. Extracts JIRA keys from commit subjects and fetches their title/description
   and parent epic from the Atlassian REST API when JIRA_USER / JIRA_TOKEN are set.
4. Sends a prompt to the Anthropic Messages API and writes the Markdown result.

No third-party dependencies required — uses Python stdlib only.
"""

import argparse
import base64
import json
import re
import subprocess
import sys
import urllib.error
import urllib.request
from os import environ

ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
ANTHROPIC_API_VERSION = "2023-06-01"
DEFAULT_MODEL = "claude-sonnet-4-6"
MAX_TOKENS = 2048
ERROR_BODY_PREVIEW_CHARS = 500
JIRA_ERROR_BODY_PREVIEW_CHARS = 200

# MAJOR.MINOR.PATCH — number of segments we keep for the user-facing short version.
SHORT_VERSION_SEGMENT_COUNT = 3

# MAJOR.MINOR.PATCH.BUILD — our release tag format.
RELEASE_TAG_GLOB = "[0-9]*.[0-9]*.[0-9]*.[0-9]*"

# Commit subjects that carry no user-facing value.
COMMIT_NOISE_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"^Prepare next development iteration\b", re.IGNORECASE),
    re.compile(r"^Bump (?:project )?version\b", re.IGNORECASE),
    re.compile(r"^Prepare release\b", re.IGNORECASE),
    re.compile(r"^\[Release]", re.IGNORECASE),
]

# JIRA enrichment — issue keys look like `MCP-123`, `CODEFIX-456`, etc.
JIRA_KEY_REGEX = re.compile(r"\b[A-Z][A-Z0-9]+-\d+\b")
JIRA_DEFAULT_BASE_URL = "https://sonarsource.atlassian.net"
JIRA_MAX_TICKETS = 40
JIRA_DESCRIPTION_MAX_CHARS = 800

# ---------------------------------------------------------------------------
# Static style examples used in the prompt. These show Claude the expected
# tone, structure and level of detail for MCP Server releases, including the
# Miscellaneous catch-all for internal/foundational work.
# ---------------------------------------------------------------------------
RELEASE_NOTES_FORMAT_EXAMPLE = """\
# SonarQube MCP Server v1.17.0

This release introduces the SonarQube configuration generator at mcp.sonarqube.com, \
fixes intermittent errors on the hosted MCP, and improves Context Augmentation guidance.

## New Features

* **Configuration generator** — Launched [mcp.sonarqube.com](https://mcp.sonarqube.com/), \
an interactive wizard to generate your SonarQube MCP Server configuration
* **Version endpoint** — Added `/info` endpoint in HTTP mode to return the running server version

## Bug Fixes

* Fixed intermittent 500 errors affecting the hosted MCP on SonarQube Cloud

## Improvements

* Enhanced Context Augmentation guidance through improved MCP instructions

---

# SonarQube MCP Server v1.18.0

This release adds pagination support for dependency risk searches, updates dependencies, \
and lays foundational groundwork for upcoming features.

## Improvements

* Added pagination support for `search_dependency_risks` tool
* Updated dependencies and resolved known CVEs

## Miscellaneous

* Continued foundational work on the declarative tool-registration framework (not yet user-facing).
* Internal refactors and dependency bumps."""


# ---------------------------------------------------------------------------
# CLI argument parsing
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate GitHub release notes for the SonarQube MCP Server.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="\n".join([
            "Environment variables:",
            "  RELEASED_VERSION    Release version (e.g. 1.19.0). Required without --tag.",
            "  CLAUDE_CODE_API_KEY Anthropic API key. Required unless --dry-run is set.",
            "  ANTHROPIC_MODEL     Override the model (default: " + DEFAULT_MODEL + ").",
            "  GH_TOKEN            Forwarded to `gh`; set by GitHub Actions.",
            "  JIRA_USER           JIRA account email for ticket context (optional).",
            "  JIRA_TOKEN          JIRA API token paired with JIRA_USER (optional).",
            "  JIRA_BASE_URL       Override the JIRA base URL (default: " + JIRA_DEFAULT_BASE_URL + ").",
        ]),
    )
    parser.add_argument("--out", metavar="FILE", help="Write generated Markdown to this file.")
    parser.add_argument("--tag", metavar="TAG", help="Released version tag (overrides RELEASED_VERSION env).")
    parser.add_argument("--dry-run", action="store_true", help="Print the prompt without calling the Anthropic API.")
    return parser.parse_args()


# ---------------------------------------------------------------------------
# Git helpers
# ---------------------------------------------------------------------------

def try_run_cmd(cmd: list[str]) -> str:
    try:
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.stdout.strip() if result.returncode == 0 else ""
    except Exception:
        return ""


def short_version(tag: str) -> str:
    parts = tag.split(".")
    return ".".join(parts[:SHORT_VERSION_SEGMENT_COUNT]) if len(parts) >= SHORT_VERSION_SEGMENT_COUNT else tag


def resolve_upper_bound(tag: str) -> str:
    """Return the tag if it already exists in git, otherwise HEAD (pre-release mode)."""
    resolved = try_run_cmd(["git", "rev-parse", "--verify", "--quiet", f"{tag}^{{commit}}"])
    if resolved:
        return tag
    print(
        f'Tag "{tag}" not found in local git; using HEAD as upper bound (pre-release mode).',
        file=sys.stderr,
    )
    return "HEAD"


def resolve_previous_tag(upper: str) -> str | None:
    prev = try_run_cmd([
        "git", "describe", "--tags", "--abbrev=0",
        f"--match={RELEASE_TAG_GLOB}", f"{upper}^",
    ])
    return prev or None


def list_commits(previous_tag: str | None, upper: str) -> list[dict[str, str]]:
    range_ = f"{previous_tag}..{upper}" if previous_tag else upper
    output = try_run_cmd(["git", "log", range_, "--no-merges", "--pretty=format:%h%x09%s"])
    if not output:
        return []
    commits = []
    for line in output.splitlines():
        tab_idx = line.find("\t")
        if tab_idx == -1:
            continue
        sha = line[:tab_idx]
        subject = line[tab_idx + 1:]
        if sha and subject and not any(p.match(subject) for p in COMMIT_NOISE_PATTERNS):
            commits.append({"sha": sha, "subject": subject})
    return commits


# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def _read_http_error_body(e: urllib.error.HTTPError, max_chars: int) -> str:
    try:
        return e.read().decode()[:max_chars]
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# JIRA enrichment
# ---------------------------------------------------------------------------

def extract_jira_keys(commits: list[dict[str, str]]) -> list[str]:
    seen: set[str] = set()
    keys: list[str] = []
    for c in commits:
        for m in JIRA_KEY_REGEX.finditer(c["subject"]):
            k = m.group(0)
            if k not in seen:
                seen.add(k)
                keys.append(k)
    return keys


def fetch_jira_ticket(base_url: str, auth_header: str, key: str) -> dict | None:
    # Atlassian REST API v2 returns description as plain text (wiki markup),
    # which is much easier to feed into a prompt than the v3 ADF JSON.
    # `parent` gives the epic context so the prompt can decide whether a ticket
    # belongs in user-facing sections or in Miscellaneous.
    url = f"{base_url}/rest/api/2/issue/{key}?fields=summary,description,issuetype,parent"
    req = urllib.request.Request(url, headers={"Authorization": auth_header, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body = _read_http_error_body(e, JIRA_ERROR_BODY_PREVIEW_CHARS)
        detail = f" — {body}" if body else ""
        print(f"  {key}: {e.code} {e.reason}, skipped{detail}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  {key}: error fetching, skipped: {e}", file=sys.stderr)
        return None

    fields = (data.get("fields") or {})
    summary = (fields.get("summary") or "").strip()
    if not summary:
        return None
    description = str(fields.get("description") or "").strip()
    if len(description) > JIRA_DESCRIPTION_MAX_CHARS:
        description = description[:JIRA_DESCRIPTION_MAX_CHARS].rstrip() + "…"
    issue_type = ((fields.get("issuetype") or {}).get("name") or "").strip()

    parent: dict | None = None
    parent_raw = fields.get("parent")
    if parent_raw and parent_raw.get("key"):
        parent_fields = parent_raw.get("fields") or {}
        status_raw = parent_fields.get("status") or {}
        parent = {
            "key": parent_raw["key"],
            "summary": (parent_fields.get("summary") or "").strip(),
            "issueType": ((parent_fields.get("issuetype") or {}).get("name") or "").strip(),
            "status": (status_raw.get("name") or "").strip(),
            # Jira's coarse status grouping: 'new' | 'indeterminate' | 'done' | 'undefined'.
            # More reliable across configurations than the human-readable status name.
            "statusCategory": ((status_raw.get("statusCategory") or {}).get("key") or "").strip(),
        }

    return {"key": key, "summary": summary, "description": description, "issueType": issue_type, "parent": parent}


def fetch_jira_tickets(commits: list[dict[str, str]]) -> list[dict]:
    base_url = environ.get("JIRA_BASE_URL", "").strip() or JIRA_DEFAULT_BASE_URL
    user = environ.get("JIRA_USER", "").strip()
    token = environ.get("JIRA_TOKEN", "").strip()
    if not user or not token:
        print("JIRA_USER / JIRA_TOKEN not set; skipping JIRA enrichment.", file=sys.stderr)
        return []
    keys = extract_jira_keys(commits)[:JIRA_MAX_TICKETS]
    if not keys:
        print("No JIRA keys found in commit subjects.", file=sys.stderr)
        return []
    print(f"Fetching {len(keys)} JIRA ticket(s) from {base_url}…", file=sys.stderr)
    auth_header = "Basic " + base64.b64encode(f"{user}:{token}".encode()).decode()
    tickets = []
    for key in keys:
        t = fetch_jira_ticket(base_url, auth_header, key)
        if t:
            tickets.append(t)
    return tickets


# ---------------------------------------------------------------------------
# Prompt construction
# ---------------------------------------------------------------------------

def render_parent_line(parent: dict | None) -> str:
    if not parent:
        return ""
    type_part = f" ({parent['issueType']})" if parent["issueType"] else ""
    summary = parent["summary"] or "(no summary)"
    status = parent["status"] or "unknown"
    category_part = f" [{parent['statusCategory']}]" if parent["statusCategory"] else ""
    return f"\n_Parent epic {parent['key']}{type_part}: {summary} — status: {status}{category_part}_"


def build_prompt(released_version: str, commits: list[dict[str, str]], jira_tickets: list[dict]) -> str:
    short = short_version(released_version)

    if commits:
        commits_text = "\n".join(f"- {c['subject']} ({c['sha']})" for c in commits)
    else:
        commits_text = "(No commits found between the previous tag and this one.)"

    if jira_tickets:
        jira_parts = []
        for t in jira_tickets:
            issue_type_part = f" ({t['issueType']})" if t["issueType"] else ""
            header = f"### {t['key']}{issue_type_part}: {t['summary']}"
            parent_line = render_parent_line(t.get("parent"))
            body = f"\n\n{t['description']}" if t["description"] else ""
            jira_parts.append(f"{header}{parent_line}{body}")
        jira_text = "\n\n".join(jira_parts)
    else:
        jira_text = "(No JIRA tickets resolved for this release.)"

    return "\n".join([
        "You are writing the GitHub release notes for the SonarQube MCP Server.",
        f"The released version is **{short}** (full tag: `{released_version}`).",
        "",
        "Below are examples of previous release notes. Match their tone, structure, and level of detail exactly:",
        "",
        RELEASE_NOTES_FORMAT_EXAMPLE,
        "",
        "Below is the list of commits included in this release (subject and short SHA).",
        'PR numbers like `(#123)` already inside subjects must be preserved verbatim.',
        'Ignore release bookkeeping commits and pure-CI changes (e.g., "Prepare next development iteration", project version bumps, GitHub Actions version/SHA bumps in `.github/workflows/`, internal CI tooling updates) — these MUST NOT appear anywhere in the release notes, not even in `## Miscellaneous`.',
        "",
        commits_text,
        "",
        "Below are the JIRA tickets referenced by those commits, with their type, title, description, and parent epic.",
        "Use them to write richer, user-facing entries when the commit subject alone is too terse,",
        "and to decide whether an item is a bug fix, a feature, internal work, or something else.",
        "",
        jira_text,
        "",
        "Now produce the release notes as Markdown, following these rules:",
        f"- Start with a single H1 heading: `# SonarQube MCP Server v{short}`.",
        "- Optionally include a one-paragraph summary right after the heading when there is a clear theme.",
        "- **User-facing-only sections**: `## New Features`, `## Bug Fixes`, `## Security`, `## Performance`, and any other themed `## ...` section MUST contain only items that an end user can observe, use, or benefit from immediately upon installing this release. A hidden tool, work-in-progress functionality, a refactor, a dependency bump, a CI/tooling change, or any foundational scaffolding MUST NOT appear in these sections.",
        '- **Litmus test for "user-facing"**: before placing an item in any non-Miscellaneous section, answer: "If a user connects to this MCP Server as they did before, will they notice anything different — a new tool available, a previously-failing call that now succeeds, different output, a noticeably faster response, or any other directly observable change?" If the honest answer is no, the item is internal and MUST go in `## Miscellaneous` (or be omitted). Restructuring code without changing externally observable behavior — even if it "enables future work", "harmonizes" an area, or "makes things easier to maintain" — is not user-facing.',
        '- **Anti-patterns (these belong in `## Miscellaneous`, not Features)**: any item whose value statement is one of "introduce/refactor X framework", "declarative framework for X", "harmonize/consolidate/unify X", "make X easier to add/maintain/extend", "foundation/groundwork/scaffolding for X", "abstraction/interface/architecture for X", "internal restructuring of X". These describe DEVELOPER ergonomics, not USER value.',
        '- **Epic-status hint**: when a ticket lists a parent epic whose status category is not `done`, treat the ticket as foundational/in-progress and place it in `## Miscellaneous` (or omit it). Only items belonging to a `done` epic — or items with no parent epic that are clearly user-facing on their own — qualify for the user-facing sections.',
        '- **Suggested section names** (recommendations, not a fixed list): `## New Features` for new user-facing functionality and enhancements, `## Bug Fixes` for user-facing fixes, `## Security`, `## Performance`, `## Improvements` etc. when they materially apply. Omit any section that would be empty.',
        '- **`## Miscellaneous`** is the catch-all for foundational work, internal improvements, dependency bumps, refactors, or items whose user impact is not visible in this release. Summarize at the level of themes rather than listing each commit/ticket. Omit the section if there is nothing meaningful to mention.',
        "- Keep entries short and user-facing. Group related commits / tickets when reasonable.",
        "- Do not include internal ticket prefixes (e.g. `MCP-123`), implementation details, or commit SHAs in the output.",
        "- Output Markdown only — no preamble, no closing remarks, no code fences around the document.",
    ])


# ---------------------------------------------------------------------------
# Anthropic API call
# ---------------------------------------------------------------------------

def call_anthropic(api_key: str, model: str, prompt: str) -> str:
    payload = json.dumps({
        "model": model,
        "max_tokens": MAX_TOKENS,
        "messages": [{"role": "user", "content": prompt}],
    }).encode()
    req = urllib.request.Request(
        ANTHROPIC_API_URL,
        data=payload,
        headers={
            "content-type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": ANTHROPIC_API_VERSION,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            text = resp.read().decode()
    except urllib.error.HTTPError as e:
        body = _read_http_error_body(e, ERROR_BODY_PREVIEW_CHARS)
        raise RuntimeError(f"Anthropic API error {e.code} {e.reason}: {body}")

    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        raise RuntimeError(f"Failed to parse Anthropic response as JSON: {text[:ERROR_BODY_PREVIEW_CHARS]}")

    if "error" in parsed:
        err = parsed["error"]
        raise RuntimeError(f"Anthropic API error: {err.get('type', 'unknown')}: {err.get('message', text)}")

    blocks = [
        b["text"].strip()
        for b in parsed.get("content", [])
        if b.get("type") == "text" and b.get("text")
    ]
    markdown = "\n\n".join(blocks).strip()
    if not markdown:
        raise RuntimeError("Anthropic response did not contain any text content")
    return markdown


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main() -> None:
    args = parse_args()
    released_version = args.tag or environ.get("RELEASED_VERSION", "")
    if not released_version:
        print("Missing released version: pass --tag or set RELEASED_VERSION.", file=sys.stderr)
        sys.exit(2)

    upper = resolve_upper_bound(released_version)

    previous_tag = resolve_previous_tag(upper)
    if previous_tag:
        print(f"Using previous tag: {previous_tag}", file=sys.stderr)
    else:
        print("No previous release tag found — using full history up to the upper bound.", file=sys.stderr)

    commits = list_commits(previous_tag, upper)
    print(f"Collected {len(commits)} commit(s).", file=sys.stderr)

    jira_tickets = fetch_jira_tickets(commits)
    print(f"Enriched with {len(jira_tickets)} JIRA ticket(s).", file=sys.stderr)

    prompt = build_prompt(released_version, commits, jira_tickets)

    if args.dry_run:
        if args.out:
            print("Note: --out is ignored in --dry-run mode (prompt is written to stdout).", file=sys.stderr)
        sys.stdout.write(prompt + "\n")
        return

    api_key = environ.get("CLAUDE_CODE_API_KEY", "")
    if not api_key:
        print("CLAUDE_CODE_API_KEY is required (use --dry-run to skip the API call).", file=sys.stderr)
        sys.exit(2)

    model = environ.get("ANTHROPIC_MODEL", "").strip() or DEFAULT_MODEL
    print(f"Calling Anthropic ({model})…", file=sys.stderr)

    markdown = call_anthropic(api_key, model, prompt)

    sys.stdout.write(markdown + "\n")
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(markdown + "\n")
        print(f"Wrote {args.out}.", file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\nFailed: {e}", file=sys.stderr)
        sys.exit(1)
