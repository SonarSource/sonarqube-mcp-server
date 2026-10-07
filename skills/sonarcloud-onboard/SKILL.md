---
name: sonarcloud-onboard
description: Assess the code quality and security of a whole repository with SonarQube Cloud. Use for questions like "what is the code quality of this project?", "is this repo clean and secure?", or "run a project health check", even when SonarQube is not named. Reuse an existing project and analysis when available; if the repository has never been analyzed, onboard it and run its first analysis before reporting findings. Also use for explicit SonarQube Cloud setup. Defaults to dev9 for this hackathon. For an isolated file or snippet, use the file-analysis skill.
---

# From repository to analyzed SonarQube Cloud project

Select this skill automatically when the user asks about the code quality, security, or health of the current project or repository, even if they do not mention SonarQube or ask for setup. A question such as "what is the code quality of this project?" calls for an analysis-backed answer: check for an existing SonarQube Cloud project and completed analysis, then onboard and analyze the repository if either is missing. Explain that you will use SonarQube Cloud on dev9 and reuse existing setup where possible. Continue through completed analysis and findings; creating a project alone does not answer the question. Do not require the user to type `$sonarcloud-onboard`. Respect an explicitly requested alternative tool or a request to avoid external analysis. An isolated file/snippet check or a specific existing SonarQube issue does not require this full onboarding workflow.

Invoking this skill, requesting repository onboarding, or asking to check the current GitHub project for security and code quality authorizes the described setup: connect the account, import or reuse its GitHub organization, start the cardless trial if unsubscribed (with automatic Free fallback if the new trial fails), import the repository, enable eligible automatic analysis, and read the results. Explain what is happening, then proceed; do not add conversational permission questions between these steps. Browser consent and GitHub permissions are still the user's actions. Ask when the repository or intended GitHub account is ambiguous, an existing subscription must change, or the requested action goes beyond this workflow. An occupied Cloud organization key is a naming collision, not an ambiguous repository target.

Run the selected backend yourself. Human interaction is limited to sign-in consent and, when needed, GitHub App installation or repository-access approval. Never ask the user to copy tokens or put credentials in tool arguments, conversation, or repository files. In CLI mode credentials stay in its system keychain; in remote MCP mode the client owns authentication and credential storage.

## Select CLI or MCP

Honor an explicitly selected backend. Otherwise prefer a supplied prototype CLI or a capable local `sonar` executable. If an explicitly selected MCP connection is broken, report the problem and offer the CLI only if the user has not ruled it out; wait for their choice before switching. If shell execution or the CLI is unavailable (including remote agents), use the SonarQube MCP connection instead. Check for `discover_github_repository`, `import_github_organization`, `ensure_cloud_subscription`, `import_github_repository`, and `get_cloud_analysis_status`. For MCP mode, read [references/mcp-onboarding.md](references/mcp-onboarding.md) and follow it instead of the CLI commands below. Shared authorization, environment, browser-handoff, subscription-preservation and findings requirements still apply.

Remote sign-in is the MCP client's OAuth connection flow, separate from onboarding tools. Do not run the CLI localhost callback remotely or invent a login tool. Use an OAuth-capable MCP deployment. Request both `read:all` and `write:all` for onboarding; read-only consent cannot expose or call setup tools. The user can sign up during browser authentication without an existing token or organization. If neither backend provides the required capabilities, report the missing capability before changing Cloud setup.

This workflow starts a 14-day Team trial with no credit card for an unsubscribed organization on dev9. The backend chooses the trial tier; the CLI omits priceId when creating the subscription. The trial pauses at expiry unless the user chooses a paid plan. If a new Team trial fails, automatically try or reuse Free under the billing recovery rules; announce the fallback without asking for another confirmation. An explicit user instruction to require Team or forbid Free takes precedence. Existing subscriptions are preserved. Never upgrade, downgrade, purchase a plan, change repository visibility, commit, or push without explicit authorization. If automatic analysis is unsupported, report that limitation and prepare an appropriate CI setup only when it is within the request; do not claim the project has been analyzed.

## Target environment: dev9

Default to **dev9** for this hackathon skill unless the user explicitly requests another environment:

- `SERVER=https://dev9.sc-dev9.io`
- `API_SERVER=https://api.sc-dev9.io`

These hosts are confirmed by dev9's `/static_configuration/configuration.json` (`environmentName: dev9`, `api.v2: https://api.sc-dev9.io`). Before making onboarding changes, read that configuration and verify it still identifies dev9 and the expected API host. If dev9 is unavailable or reports a different environment, stop and report it; do not fall back to production.

The CLI needs **both** host overrides on **every invocation** so dev9 is classified as Cloud and v1 and v2 requests go to the same environment:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" auth status --format json
```

In all command examples below, invoke `"$CLI"` with this environment prefix. For shorter commands in bash or zsh, define a function in each shell invocation that needs it:

```sh
# Set CLI to the resolved absolute executable path first.
SERVER=https://dev9.sc-dev9.io
API_SERVER=https://api.sc-dev9.io
E() {
  env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
      SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
      "$CLI" "$@"
}
E auth status --format json
```

Do not store an `env ...` command in a string and execute `$E`: zsh does not split it into words. Do not assume variables or functions persist across shell tool calls; repeat the definition or substitute quoted literal values.

A successful authentication check against production does not authorize dev9 operations. Check the JSON `server` value equals `SERVER` before continuing; otherwise authenticate explicitly to dev9. Keep any existing production connection separate. Do not copy production tokens or reuse production organization/project bindings for this test.

The GitHub App returned by dev9's `show_app_info` must be the App for that environment, with a Setup URL returning to dev9. A return to `sonarcloud.io` or `sonarqube.us` indicates mismatched configuration: report it instead of completing web onboarding there. With the handoff frontend deployed, the CLI's `state=sonarqube-cli` should show “Continue in your agent” on dev9. The CLI verifies installation independently; the page is only a handoff message.

## Guide the user through the flow

Own the whole journey in the agent conversation. Before starting, name the repository and dev9, explain the value of the check, and preview the two possible browser approvals. For example: “I’ll set up SonarQube Cloud in dev9 to check `OWNER/REPO` for security issues. New setup tries a 14-day Team trial with no credit card required and falls back to Free if that new trial fails. You may need to approve sign-in and repository access; I’ll handle the rest.” Target at most one Cloud sign-in and one GitHub approval when new setup needs both; existing setup should need neither. Treat extra browser sign-ins as a recovery cost to explain and minimize. Reuse completed steps instead of making the user repeat them.

Keep a short progress summary: **account connected → repository access → analysis**. Say explicitly who acts next: “waiting for you in the browser” or “no action needed from you.” Explain every browser handoff **before** opening it:

- Sign-in: “First, connect your account. I’m opening SonarQube Cloud. Choose GitHub and approve the connection, then return here.”
- Repository access: “Your account is connected. Next, SonarQube Cloud needs access to `OWNER/REPO`. GitHub will show the requested permissions; select the account containing the repository and grant access to it.”
- After permissions and the repository listing are verified: “Repository access is confirmed. You can close the browser tab. I’m preparing the project and starting analysis.”
- Analysis: “Setup is complete. SonarQube Cloud is checking your repository. No action is needed from you.”
- Completion: identify the analyzed branch/revision/date, explain the findings, and give the dashboard URL. Setup success alone does not mean the project is secure.

### Command permissions

Honor the agent environment's sandbox and approval policy. When an authorized command is blocked by network, keychain, local callback or browser-launch restrictions, use its supported approval mechanism; do not interpret a sandbox DNS failure as proof that dev9 is down. Where the tool supports reusable command-prefix approval, propose a narrow prefix for the resolved executable, exact dev9 environment overrides and relevant subcommand. Never request a blanket shell or `env` allow rule, change the user's permission settings, or bypass a rejection. Distinguish these execution approvals from the two browser consent steps. Use concise explanations of the action and why execution needs approval.

Batch independent read-only checks when supported. Once authentication is confirmed, start `org import` and open an installation URL only if that running command emits `browser_required`; do not open GitHub proactively or repeat a successful approval. A normal trial and analysis should continue without billing diagnosis commands. After a billing failure, use the bounded read-only diagnostic and recovery procedure in [references/billing-recovery.md](references/billing-recovery.md).

### Prepare, explain, open, wait, confirm

Use `--no-browser --events` for browser-producing commands. Login also needs `--non-interactive`. The CLI prepares the action and waits without opening a browser. Run these commands with your shell tool's background/yield/session support so you can read output while the same process keeps running; do not kill or restart the process when a browser action is reported. macOS does not ship GNU `timeout`; use the command’s own `--timeout` where supported and shell-tool background sessions with monitored output. Keep waits short enough to provide progress updates.


File-based event monitors must stop with their producer; a bare `tail -F` keeps running after the command exits. Use a separate log for each command, created before starting it. Have the command wrapper append `exit=<status>` to the event log after collecting the command's exit status on both success and failure (for example, `printf 'exit=%s\n' "$command_exit" >> "$EVENT_LOG"`). Keep final stdout JSON in a separate file so this sentinel does not corrupt it. The sentinel is wrapper metadata, not a CLI JSON event or proof of success.

Watch newly appended lines with a terminating loop, then drain the final lines:

```sh
seen_lines=0
read_new_lines() {
  line_count=$(wc -l < "$EVENT_LOG")
  if [ "$line_count" -gt "$seen_lines" ]; then
    sed -n "$((seen_lines + 1)),${line_count}p" "$EVENT_LOG"
    seen_lines=$line_count
  fi
}
until grep -q '^exit=' "$EVENT_LOG"; do
  read_new_lines
  sleep 1
done
read_new_lines
```

Read the actual exit status before continuing. If the producer dies without writing its sentinel, detect that through the background session/process status and stop the watcher with an incomplete-command report. Cancel the associated watcher when its command is cancelled; do not leave monitors armed between onboarding steps.

Structured events are JSON lines on **stderr**, alongside ordinary diagnostics. Final organization/analysis JSON stays on stdout. Read every stderr line, including ordinary warnings and error lines (such as `❌ Subscription creation failed (POST /billing/subscriptions)…`). Filter only when parsing events; never discard the rest of stderr. For events, parse only JSON objects with `schemaVersion: 1` and an `event` field:

- `browser_required`: contains `step`, `actor: user`, `url`, and `message`. Explain the purpose and what the user must do in that page. Read `url` from the raw stderr JSON, before notification or UI rendering can turn `&` into `&amp;`. JSON-decode the field; do not copy an HTML-escaped display link or reconstruct the URL. Then open the **exact returned URL** with the same environment overrides:

  ```sh
  env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
      SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
      "$CLI" browser open "$ACTION_URL"
  ```

  For `connect_account`, verify the URL belongs to `SERVER`; for `github_access`, verify HTTPS and `github.com`. Treat the URL as data, quote it, and do not reconstruct or change its port/state. If browser opening fails, provide the exact link for the user to open manually and keep the original waiting process alive.

- `step_started` / `step_completed`: update the progress summary briefly. Skip announcements for repeated diagnostics. A completed GitHub App connection does not prove a particular repository was granted: confirm the requested slug appears in the repository listing before saying its access is verified.
- `step_failed`: explain which stage stopped and which stages completed. Use the accompanying CLI diagnostic to identify the corrective action. Preserve the existing organization/project; do not reset setup, repeat successful approvals, or send the user into web signup automatically. Never render a raw HTTP code as the only explanation.

After opening the browser, keep waiting on the original command for consent/install detection and completion. Do not end the workflow after printing a URL. Do not say a browser has opened until its opener succeeds, and do not claim an installation or analysis succeeded based on a redirect.

## Resolve the repository and CLI

1. Work from the repository root. Read `git remote get-url origin` and extract the GitHub owner and repository from HTTPS or SSH remotes, stripping the optional `.git` suffix. Only use `github.com` remotes. Ask for the target when there is no unambiguous GitHub remote. Treat names as data: quote every shell argument and never evaluate remote URLs as shell code.
2. Use the dev9 web/API pair above. If the user explicitly requests another environment, resolve its web/API pair and use it consistently for the entire run; do not infer the environment from a previously active connection.
3. Prefer the absolute executable path supplied in `SONAR_ONBOARD_CLI`; otherwise resolve `sonar` on PATH. Check `auth login --help`, `org import --help`, and `project wait --help` for `--no-organization`, `--non-interactive`, `--no-browser`, and `--events` on login; `--github`, `--no-browser`, and `--events` on org import; and `--repo` and `--events` on project wait. Also check `browser open --help`. **These onboarding commands are a prototype: downloading today's stable CLI does not necessarily provide them.** Do not replace a supplied prototype with a stable release or call self-update on it.
4. If a usable CLI is absent, first use the MCP path above when its tools are available. If MCP is also unavailable, an official stable installation can be performed with the user's installation authorization:

   ```sh
   curl -fsSL https://raw.githubusercontent.com/SonarSource/sonarqube-cli/refs/heads/master/user-scripts/install.sh | bash
   ```

   On Windows use the corresponding `user-scripts/install.ps1`. Check capabilities again after installation. If the commands are missing, use a supplied prototype binary. If a local prototype checkout and Bun are available, run `bun install --frozen-lockfile` and `bun build-scripts/build-binary.ts` there, then use its `dist/sonarqube-cli`. Otherwise request the prototype binary or source location and stop before making Cloud changes.

Use the same executable for every remaining command. In examples, `CLI` is its absolute path, `SERVER` and `API_SERVER` are the selected environment's web and v2 API URLs, and `OWNER/REPO` is the validated GitHub slug. Adapt shell syntax for the host platform.

## Reuse authentication and discover existing setup

Check `"$CLI" auth status --format json`. Reuse credentials only when the JSON reports `status: connected` **and** `server` equals `SERVER`. If the token is missing, invalid, or belongs to another environment, run the login below. If a verified organization key is already known, replace `--no-organization` with `--org "$ORG_KEY"` on this first login to avoid a later selection sign-in:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" auth login --server "$SERVER" --no-organization --non-interactive --no-browser --events
```

`--non-interactive --no-browser --events` prepares the sign-in URL, suppresses terminal prompts, and waits only for a validated browser callback. Explain and open the reported action as described above. Tell the user to choose GitHub in the browser, sign in or sign up, and approve the token exchange. Wait for the command to exit successfully. Do not set `SONARQUBE_CLI_DISABLE_BROWSER` unless opening the browser is unavailable; in that case show the CLI's login URL and wait for the same callback. If authentication environment variables override a different saved identity or environment, explain the conflict and resolve it with the user's intended credentials; never print their values.

If login reuses a saved token but Cloud subsequently rejects it with HTTP 401, perform one forced login and wait for validated consent. When the organization is already known, use `--org "$ORG_KEY"` instead of `--no-organization` on that forced login so it also selects the organization. Do not repeat forced logins indefinitely. A subsequent billing 404 is a separate propagation/service problem; follow the billing recovery reference rather than signing in again.

After authentication, perform the existing-project lookup below before provisioning. `org import` already discovers/reuses the GitHub-bound organization; the manual organization search is needed for early project reuse or billing recovery, not as a second mandatory organization-discovery step. Use a future `org status --github` shortcut only if the executable’s help confirms it exists.

### Existing project first

Use the confirmed target environment and repository identity to find a matching existing project. A valid login skips sign-in; it does not prove that the repository already has a project. Prefer a user-specified organization/project or repository configuration as a candidate, but verify it belongs to this environment and repository. Do not select a project merely because its name resembles the repository.

Read `/api/organizations/search?member=true&p=1&ps=100` (paginate to completion) to identify an accessible organization whose `alm.key` is `github` and whose `alm.url` identifies the target owner. Reviewing existing analysis does not require organization-admin permissions; require admin only for setup changes. A saved organization key is also a candidate, not proof of a match. For the matching organization, read `/api/alm_integration/list_repositories?organization=<encoded-key>` and resolve the exact repository slug and its `linkedProjects`. Apply both environment overrides to every `api get` call. Preserve authorization and network errors as errors; do not interpret them as missing setup.

- One linked project: reuse its returned organization and project keys. Skip `org import`, App installation, trial/subscription creation and repository import. Go directly to completed-analysis lookup and result inspection, checking/selecting the resolved organization as described below before commands that need a selected organization. Existing setup does not need to be provisioned again.
- Several linked projects: ask which project to review unless the user or verified repository configuration already identifies it.
- Matching organization but no linked project: preserve that organization and subscription, then import only the missing repository project. Do not start another trial or install the App again when access is already confirmed. Check/select that organization before subsequent commands that require it.
- No matching organization or repository access: continue with the missing onboarding steps below. A genuinely unbound organization may be reused and bound by `org import`.

For a matching project with a completed analysis, fetch its default branch, analysis date/revision, quality gate and findings immediately. Report the freshness of that analysis. Do not rerun setup or claim that a historical analysis covers local uncommitted changes. If no completed analysis exists, use `project wait --project <resolved-key>` to activate eligible analysis and wait; this still does not require onboarding the existing project again. If the user explicitly requests a fresh analysis, use the supported analysis path for that project rather than recreating it.

## Onboard only missing organization setup

Run this only when discovery established that organization setup or binding is missing:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" org import --github "$OWNER" --plan team-trial --timeout 600 --no-browser --events --format json
```

This reuses an administrable GitHub-bound Cloud organization, or finds an unbound installation and creates/binds the organization. If no installation is found, it reports a GitHub browser action and polls for installation while the agent explains and opens that URL. Tell the user to select the target GitHub account and grant the App access to `OWNER/REPO`. GitHub normally redirects back to the Cloud onboarding/plan-selection page. That page is not a required continuation of the CLI flow: tell the user to return to the agent once installation is complete. They need not manually create the Cloud organization, select a plan, or start a Team trial on that page. Keep waiting for the running CLI to finish; surface any CLI error instead of silently substituting web signup. The CLI starts a cardless Team trial when no subscription exists, verifies an active Team trial, expiry and absence of a payment method, and selects the organization for subsequent commands. Read `plan`, `trial`, `subscriptionStatus` and `trialPeriod` from its JSON output. Existing Free or other subscriptions are reused; do not upgrade or replace them to obtain a trial. If a new Team trial fails, announce and automatically attempt or reuse Free under [references/billing-recovery.md](references/billing-recovery.md), without another confirmation. Never retry with a paid price or change an existing subscription. `--plan free` does not bypass the existing-subscription lookup and can fail with the same billing 404; a failed or uncertain billing request is not evidence that another signup is safe. Report the actual fallback plan and its verification source, never a successful trial.

Parse `organizationKey` from successful JSON; never assume it equals the GitHub owner. Honor a user-specified Cloud key. Otherwise, choosing an available key for a new organization is part of onboarding and requires no separate confirmation.

Prefer reusing the existing administrable organization. `org import` first discovers an organization already bound to the target GitHub owner. If none is bound, it checks the requested key (default: GitHub owner), reuses an unbound organization at that key, and connects the existing GitHub App installation with `/api/alm_integration/bind_organization`. This is part of onboarding and requires no additional conversational confirmation. Preserve its subscription; an existing Free subscription remains Free, and an existing Team trial is reused. Only start a trial when no subscription exists. Verify the requested repository is listed after binding.

Do not replace a binding to a different DevOps account. If the default key belongs to a differently bound organization, choose an available `<owner>-dev9` key for this environment (adding a numeric suffix if needed) and explain the choice. If the user explicitly requested that differently bound organization, ask before rebinding it or substituting a new organization. Authorization or network errors never establish that an organization or subscription is absent. If an App installed by another administrator is not discoverable, obtain its non-secret numeric installation ID and rerun with `--installation-id <id>`; do not install a duplicate App blindly. Administrator approval may be required by GitHub. `--no-browser` prints the URL and still polls.

### Confirm organization selection

Before `import` or `project wait --repo`, run `E auth status --format json` and require `status: connected`, `server: SERVER`, and `org: ORG_KEY`. A partial `org import` failure can leave the organization created/bound but unselected: selection happens only after successful subscription handling. Do not assume a browser redirect or a discovered organization selected it.

`SONARQUBE_CLI_ORG` alone is ignored with saved/keychain credentials; it is not a general organization override. It participates only in complete environment authentication with `SONARQUBE_CLI_TOKEN` (and the intended server). If that mode is already in use, set its non-secret org value and verify status; never extract or copy a keychain token to activate it. The current prototype `import` has no `--org` override.

Prefer `E org use "$ORG_KEY"` only if `org use --help` confirms a supported selection command; the current prototype does not provide it. Otherwise use `E auth login --server "$SERVER" --org "$ORG_KEY" --non-interactive --no-browser --events` without `--force`. It may reuse credentials already saved for that organization; the current prototype stores tokens by server/org, so selecting a previously unselected organization can still require browser consent. Explain that limitation. If a forced login is needed anyway, combine it with `--org` once the key is known. Never edit auth state or move tokens manually. Recheck status before import.

If discovery fails with `Identity provider 'OTHER' is not supported` or `User is not identified with any of the supported identity providers`, the current Cloud identity is incompatible with these legacy GitHub onboarding APIs. A valid SSO token and membership in a GitHub-bound organization do not imply GitHub identity. Tell the user to sign out of the browser's SSO session and choose GitHub, then run:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" auth login --server "$SERVER" --no-organization --non-interactive --no-browser --events --force
```

`--force` prepares a fresh token exchange instead of silently reusing a stored token. If `ORG_KEY` is already known and verified, replace `--no-organization` with `--org "$ORG_KEY"` here too. Explain and open its new `browser_required` action before waiting for approval. Retry organization import only after the browser login succeeds. If the user needs to keep using SSO, stop this onboarding path and explain that a separate GitHub authorization flow is needed; do not retry installation IDs or infer that a PAT can change the account's identity provider.

## Import or reuse the project

Confirm the selected organization with `auth status` as described above, then query the real binding before provisioning so retries do not create another project:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" api get "/api/alm_integration/list_repositories?organization=$ORG_KEY"
```

URL-encode `ORG_KEY` if needed. Parse `repositories`; identify the exact target by `slug`, falling back to the part of `installationKey` before `|`. If the repository is absent, check App repository access and permissions; do not fabricate an installation key or make the repository public.

- If `linkedProjects` contains one project, reuse its exact key.
- If it is empty, run `"$CLI" import --repo "$OWNER/$REPO" --non-interactive` and wait for success.
- If several projects are linked, this is a monorepo: obtain the intended project key instead of guessing.

`import` returning successfully means a project was provisioned. It does **not** prove an analysis completed.

## Wait for analysis and inspect the result

For a single-project repository:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" project wait --repo "$OWNER/$REPO" --timeout 900 --events --format json
```

For a monorepo, use `--project "$PROJECT_KEY"` instead of `--repo`. This requests eligibility and automatic-analysis activation when no analysis exists, handles asynchronous eligibility checks, and polls completed default-branch analyses. Successful JSON contains `projectKey`, `analysisId`, `analysisDate`, and `url`. An existing analysis in dev9 counts as an analyzed project; it is not a guarantee that the current local working tree or commit was analyzed. Compare revision/date if the request requires a fresh result.

Timeouts are resumable: keep the imported organization/project, report progress, and rerun the same wait command when appropriate. Authentication, authorization, subscription, or eligibility errors require corrective action; do not blindly retry indefinitely. For unsupported automatic analysis, explain the reported reason and use the supported scanner/CI for the repository's language/build system within the authorized scope. Until a completed analysis is verified, report onboarding as incomplete.

Once complete, resolve the analyzed default branch with `api get "/api/project_branches/list?project=$PROJECT_KEY"` (URL-encode the key and apply both dev9 overrides). Choose the entry where `isMain` is true and use its name as `MAIN_BRANCH`. Always select this branch explicitly; otherwise the CLI may auto-select a pull request from the local checkout. Fetch every issues page when summarizing totals or security findings.

Then run:

```sh
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" quality-gate status --project "$PROJECT_KEY" --branch "$MAIN_BRANCH" --format json
env SONARQUBE_CLI_SONARCLOUD_URL="$SERVER" \
    SONARQUBE_CLI_SONARCLOUD_API_URL="$API_SERVER" \
    "$CLI" list issues --project "$PROJECT_KEY" --branch "$MAIN_BRANCH" --format json
```

Use the actual returned project key. Treat a failed quality gate and reported security issues as findings to inspect, not as proof of onboarding failure. Report the actual plan and trial expiry when present; explain that a cardless trial pauses at expiry unless the user chooses a paid plan. Report the environment (dev9), Cloud organization, project key, analysis date, dashboard URL, quality-gate result, and actionable findings. Verify the returned dashboard URL uses the selected web host. A successful analysis alone does not mean the project is secure.


### Verify indexing, hotspots, and branch coverage

`project wait` currently confirms a completed default-branch analysis, not issue-search indexing. Before reporting findings, read overall-code measures for that same project and branch:

```sh
# URL-encode PROJECT_KEY and MAIN_BRANCH before constructing these queries.
E api get "/api/measures/component?component=$PROJECT_KEY&branch=$MAIN_BRANCH&metricKeys=vulnerabilities,bugs,code_smells,security_hotspots,security_rating,reliability_rating,sqale_rating"
E api get "/api/hotspots/search?projectKey=$PROJECT_KEY&branch=$MAIN_BRANCH&p=1&ps=100"
```

The hotspots parameter is **`projectKey`**, not `project`. Paginate hotspots separately from issues; report their review status rather than counting them as vulnerabilities. A failed hotspots request means review is incomplete, not zero hotspots.

If `vulnerabilities + bugs + code_smells > 0` but the issues search is empty, poll the same explicitly scoped search every 10 seconds for up to 2 minutes. Use `E list issues --project "$PROJECT_KEY" --branch "$MAIN_BRANCH" --format json` or `/api/issues/search?componentKeys=<encoded-project>&branch=<encoded-branch>&p=1&ps=100`. Do not combine different branches or filtered issue totals. Re-read measures if the counts still disagree; report indexing as incomplete with the observed measures and search totals if the deadline expires. Never announce zero issues or a clean project while measures show findings. Future CLI versions may include indexing in `project wait`; check capability rather than assuming this prototype does.

A first analysis can report quality gate `NOT_COMPUTED` because the new-code baseline is not established yet. Report it as not computed, not as a failed gate or a passed gate. Use overall-code ratings and measures to describe the findings meanwhile. If the cause is not known, state that uncertainty.

Only the reported analyzed branch/revision is covered. This onboarding path waits for the default branch; it does not analyze every feature branch. Compare `git branch --show-current` and `git rev-parse HEAD` with the main branch and analysis revision (retrieve `/api/project_analyses/search?project=<encoded-project>&branch=<encoded-branch>&p=1&ps=1` if needed). If the checkout differs, explicitly say the local branch/commit was not verified by this result. Never imply local uncommitted changes were analyzed. Request a separate supported branch/CI analysis only within the user's scope.

### Final report

Use this fixed summary, with unavailable fields marked unknown and failed retrievals marked incomplete:

- **Environment, organization, project:** selected host, organization key, project key.
- **Plan or trial:** actual reported plan, source of verification, trial status/expiry if verified; disclose automatic Free fallback and any incomplete billing verification.
- **Analysis coverage:** branch, commit/revision, completion date, comparison with the local checkout and freshness.
- **Quality gate:** status and failed conditions when available; explain `NOT_COMPUTED` without presenting it as failure.
- **Findings:** issue counts by type and severity using returned taxonomy; hotspots counted separately with review status; overall-code ratings/measures. Paginate before claiming complete totals.
- **Top findings:** actionable rules, locations and impact, with links where returned.
- **Deviations:** billing/auth recovery, extra sign-ins, backend switch, indexing delay, unsupported analysis or incomplete checks. Say none if the normal flow completed.
- **Dashboard:** verified link on the selected web host.

A completed analysis alone does not mean the project is secure. Incomplete searches or unavailable severity totals must remain explicit in the report.
