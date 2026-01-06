---
name: "sonarqube"
displayName: "SonarQube Code Quality & Security"
description: "Integrate with SonarQube for code quality analysis, security scanning, and technical debt management - detect bugs, vulnerabilities, and code smells"
keywords: ["sonarqube","issues","code-quality","security","analysis","quality-gates","vulnerabilities"]
author: "Sonar"
---

# SonarQube Code Quality & Security Power

## Overview

Integrate with SonarQube Server or Cloud to analyze code quality, detect security vulnerabilities, and manage technical debt. This power provides seamless access to SonarQube's comprehensive code analysis platform, enabling you to detect bugs, security vulnerabilities, code smells, and enforce quality standards throughout your development workflow.

SonarQube supports 30+ programming languages and provides actionable insights to help teams write cleaner, safer, and more maintainable code. Use it to analyze code snippets on-the-fly, search for issues, track quality metrics, and ensure your projects meet quality gate standards before deployment.

**Key capabilities:**

- **Code Analysis**: Analyze code snippets and files directly within your development context
- **Issue Management**: Search, filter, and manage code quality issues across projects
- **Quality Gates**: Monitor and enforce quality standards before deployment
- **Security Scanning**: Detect security vulnerabilities and hotspots
- **Metrics & Measures**: Track code coverage, complexity, duplications, and technical debt
- **Rules & Standards**: Access comprehensive rule sets for coding standards
- **Dependency Risks**: Identify vulnerabilities in third-party dependencies (SCA)
- **Project Management**: Browse projects, portfolios, and quality status

**Authentication**: Requires a SonarQube user token. For SonarQube Cloud, you'll also need your organization key. For SonarQube Server, you'll need your server URL.

## Available MCP Servers

### sonarqube

**Connection:** Docker container (stdio transport)

**Container Image:** `mcp/sonarqube` from Docker Hub

**Authorization:** User token authentication with environment variables

The server supports both SonarQube Cloud and on-premises SonarQube Server instances.

## Best Practices

### Integration Approach

**For SonarQube Cloud:**
- Use your organization key from [SonarQube Cloud Organizations](https://sonarcloud.io/account/organizations)
- Connect to the US instance by setting `SONARQUBE_CLOUD_URL` if needed
- Token should have appropriate permissions for the projects you want to analyze

**For SonarQube Server:**
- Always use USER tokens (not project or global tokens)
- Ensure your SonarQube Server is accessible from your development environment
- For self-signed certificates, mount custom certificates using volume mounts

### Code Analysis

**Always specify the programming language** when analyzing code snippets to improve accuracy:

```javascript
// Analyze TypeScript code
analyze_code_snippet({
  codeSnippet: "function foo() { console.log('test'); }",
  language: "typescript",
  projectKey: "my-project"
});
```

### Issue Management

**Understanding Issue Types:**

- **Bugs**: Code that is wrong or will behave unexpectedly - fix immediately if HIGH or BLOCKER severity
- **Vulnerabilities**: Security issues that could be exploited - always prioritize BLOCKER and HIGH severity
- **Code Smells**: Maintainability issues - address during refactoring, track with sqale_index metric

**Filter issues effectively** to focus on what matters:

```javascript
// Search for high-severity security issues
search_sonar_issues_in_projects({
  projects: ["my-project"],
  severities: ["HIGH", "BLOCKER"],
  p: 1,
  ps: 100
});
```

**Managing Issue Status:**

Before marking issues as false positives:
1. Read the rule description carefully with `show_rule`
2. Understand why the rule exists
3. Consider if there's a better way to write the code
4. Document the reasoning
5. Discuss with team if uncertain

Use `change_sonar_issue_status` appropriately:
- `accept` - Acknowledged, will be addressed later
- `falsepositive` - Not actually an issue (document why)
- `reopen` - Previously closed but needs reconsideration

### Quality Gates

Quality gates are **release readiness indicators** that enforce minimum quality standards and prevent regression. Never bypass failed quality gates without team discussion.

**Check quality gate status** before merging:

```javascript
// Check quality gate for a pull request
get_project_quality_gate_status({
  projectKey: "my-project",
  pullRequest: "123"
});
```

**Best practices**:
- Understand which conditions failed and why
- Fix issues rather than relaxing gate conditions
- Configure gates to be strict but achievable
- Use different gates for different project types if needed

### Essential Metrics

**Code Quality Metrics:**
- `ncloc` - Lines of code
- `bugs` - Number of bug issues
- `vulnerabilities` - Security vulnerabilities
- `code_smells` - Maintainability issues
- `sqale_index` - Technical debt (time to fix)
- `sqale_rating` - Maintainability rating (A-E)

**Test Coverage:**
- `coverage` - Overall coverage percentage
- `line_coverage` / `branch_coverage` - Coverage by type
- `uncovered_lines` - Lines without tests

**Complexity:**
- `complexity` - Cyclomatic complexity
- `cognitive_complexity` - Code understandability
- `duplicated_lines_density` - Duplication percentage

### Advanced Features

**Selective Tool Enablement** - Reduce context overhead by enabling only needed toolsets:

```bash
# For code analysis workflow
docker run -i --rm \
  -e SONARQUBE_TOOLSETS="analysis,issues,quality-gates" \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  mcp/sonarqube
```

Available toolsets: `analysis`, `issues`, `quality-gates`, `rules`, `sources`, `measures`, `languages`, `portfolios`, `system`, `webhooks`, `dependency-risks`. Note: `projects` is always enabled.

**Read-Only Mode** - Disable write operations for safer exploration:

```bash
docker run -i --rm \
  -e SONARQUBE_READ_ONLY="true" \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  mcp/sonarqube
```

## Common Workflows

### Workflow 1: Analyze Code Snippet Before Committing

```javascript
// Step 1: Analyze the code snippet
const analysis = analyze_code_snippet({
  codeSnippet: `
    function calculateTotal(items) {
      var total = 0;
      for (var i = 0; i < items.length; i++) {
        total += items[i].price;
      }
      return total;
    }
  `,
  language: "javascript",
  projectKey: "my-project"
});

// Step 2: Review detected issues
// Step 3: Fix issues in your code
// Step 4: Re-analyze to verify fixes
```

### Workflow 2: Review Issues in Pull Request

```javascript
// Step 1: Search for issues in the pull request
const issues = search_sonar_issues_in_projects({
  projects: ["my-project"],
  pullRequestId: "123",
  severities: ["HIGH", "BLOCKER"]
});

// Step 2: Review each issue
for (const issue of issues) {
  const rule = show_rule({ key: issue.rule });
  // Understand the rule and remediation
}

// Step 3: Check quality gate status
const qgStatus = get_project_quality_gate_status({
  projectKey: "my-project",
  pullRequest: "123"
});

// Step 4: Determine if PR can be merged
```

### Workflow 3: Monitor Project Health

```javascript
// Step 1: Get project measures
const measures = get_component_measures({
  projectKey: "my-project",
  metricKeys: [
    "ncloc",
    "coverage",
    "bugs",
    "vulnerabilities",
    "code_smells",
    "sqale_index"
  ]
});

// Step 2: Check quality gate status
const qgStatus = get_project_quality_gate_status({
  projectKey: "my-project"
});

// Step 3: Search for unresolved issues
const issues = search_sonar_issues_in_projects({
  projects: ["my-project"],
  severities: ["HIGH", "BLOCKER"]
});

// Step 4: Generate report or alert if thresholds exceeded
```

### Workflow 4: Analyze Dependencies for Security Risks

```javascript
// Step 1: Search for dependency risks (requires Server 2025.4+ Enterprise with Advanced Security)
const risks = search_dependency_risks({
  projectKey: "my-project",
  branchKey: "main"
});

// Step 2: Review high-severity vulnerabilities
// Step 3: Update vulnerable dependencies
// Step 4: Re-analyze to verify fixes
```

## Configuration

**Authentication Required**: 
- **For SonarQube Cloud**: User token + organization key
- **For SonarQube Server**: User token + server URL

**Setup Steps:**

### For SonarQube Cloud:

1. Navigate to [SonarQube Cloud](https://sonarcloud.io)
2. Go to Account → Security → Generate Tokens
3. Generate a user token with appropriate permissions
4. Note your organization key from [Organizations](https://sonarcloud.io/account/organizations)
5. Configure in your MCP client

### For SonarQube Server:

1. Log in to your SonarQube Server instance
2. Navigate to User → My Account → Security
3. Generate a USER token (not project or global token)
4. Note your server URL (e.g., `https://sonarqube.company.com`)
5. Configure in your MCP client

**MCP Configuration:**

For SonarQube Cloud:

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e",
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_ORG",
        "mcp/sonarqube"
      ],
      "env": {
        "SONARQUBE_TOKEN": "<your-token>",
        "SONARQUBE_ORG": "<your-org>"
      }
    }
  }
}
```

For SonarQube Server:

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e",
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_URL",
        "mcp/sonarqube"
      ],
      "env": {
        "SONARQUBE_TOKEN": "<your-token>",
        "SONARQUBE_URL": "<your-server-url>"
      }
    }
  }
}
```

## Troubleshooting

### Error: "Authentication failed"

**Cause:** Invalid or expired token
**Solution:**

1. Verify token is valid and not expired
2. Regenerate token if needed
3. Ensure token has correct permissions
4. For Server, verify you're using a USER token (not project/global)

### Error: "Project not found"

**Cause:** Invalid project key or insufficient permissions
**Solution:**

1. Verify project key is correct
2. Check project exists in SonarQube dashboard
3. Ensure token has access to the project
4. For Cloud, verify you're using the correct organization

### Error: "Connection refused"

**Cause:** Cannot connect to SonarQube Server
**Solution:**

1. Verify `SONARQUBE_URL` is correct
2. Check server is accessible from your network
3. For self-signed certificates, mount custom certificates
4. Check proxy configuration if behind corporate proxy

### Issues not appearing in search

**Cause:** Project not analyzed or permissions issue
**Solution:**

1. Ensure project has been analyzed at least once
2. Check analysis completed successfully
3. Verify project is not empty
4. Ensure token has "Browse" permission on project
5. Try refreshing the project analysis

### Quality gate status unavailable

**Cause:** No analysis or quality gate not configured
**Solution:**

1. Ensure project has been analyzed
2. Verify quality gate is assigned to project
3. Check analysis completed successfully
4. For branches/PRs, ensure they've been analyzed
5. Review project settings in SonarQube dashboard

### Code snippet analysis fails

**Cause:** Language not supported or analysis error
**Solution:**

1. Specify correct language parameter
2. Verify language is supported (use `list_languages`)
3. Check code snippet is valid syntax
4. Provide project key for better context
5. Review MCP server logs at `/app/storage/logs/mcp.log` in the container

## Tips

1. **Start with project discovery** - Use `search_my_sonarqube_projects` to find available projects
2. **Use quality gates as checkpoints** - Don't merge code that fails quality gates
3. **Analyze code snippets frequently** - Catch issues early in development
4. **Review rule details** - Understand why issues are flagged and how to fix them
5. **Monitor trends, not just values** - Track if metrics are improving or degrading over time
6. **Integrate with CI/CD** - Check quality gates in your pipeline before deployment
7. **Use selective toolsets** - Enable only what you need to reduce context overhead
8. **Prioritize security issues** - Fix BLOCKER and HIGH vulnerabilities immediately

## Resources

- [SonarQube MCP Server GitHub](https://github.com/SonarSource/sonarqube-mcp-server)
- [SonarQube Documentation](https://docs.sonarsource.com/sonarqube)
- [SonarQube Rules](https://rules.sonarsource.com)
