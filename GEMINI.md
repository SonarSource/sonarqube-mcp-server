# SonarQube MCP Server - User Context

## What This MCP Server Provides

This MCP server gives you access to SonarQube's code quality and security analysis tools directly in your AI conversations. You can analyze code, check project quality, manage issues, and get detailed insights without leaving your chat.

## Setup

The user should provide the following environment variables:
- `SONARQUBE_TOKEN`: A SonarQube USER token
- `SONARQUBE_URL`: The URL of the SonarQube server (for SonarQube Server)
- `SONARQUBE_ORG`: The organization key (for SonarQube Cloud)

## How Users Typically Interact

### Finding Projects
**Example user requests:**
- "Show me my SonarQube projects"
- "List all projects in my organization"

**What to do:** Use `search_my_sonarqube_projects` to get the list. The results will show project keys that are needed for other operations.

### Analyzing Code Quality
**Example user requests:**
- "What's the quality gate status of my project?"
- "Check if my project passes quality gates"
- "How is the code quality for project X?"

**What to do:** Use `get_project_quality_gate_status` with the project key. If user doesn't know the project key, first use `search_my_sonarqube_projects`.

### Code Issues and Violations
**Example user requests:**
- "Show me the issues in my project"
- "Find security issues in project X"
- "List all bugs in my codebase"

**What to do:** Use `search_sonar_issues_in_projects`. You can filter by project, issue type, severity, etc.

### Code Snippet Analysis
**Example user requests:**
- "Analyze this code snippet for issues"
- "Check this code for quality problems"
- "What would SonarQube say about this file?"

**What to do:** Use `analyze_code_snippet` with the provided code and specify the programming language.

### Understanding Rules and Metrics
**Example user requests:**
- "What does this rule mean?" 
- "Explain rule javascript:S1234"
- "What metrics are available?"
- "Show me code complexity metrics"

**What to do:** Use `show_rule` for rule explanations, `search_metrics` for available metrics, and `get_component_measures` for specific metric values.

## Important Parameter Guidelines

### Project Keys
- When a user mentions a project key, use `search_my_sonarqube_projects` first to find the exact project key
- Don't guess project keys - always look them up

### Code Language Detection
- When analyzing code snippets, try to detect the programming language from the code syntax
- If unclear, ask the user or make an educated guess based on syntax

### Branch and Pull Request Context
- Many operations support branch-specific analysis
- If user mentions working on a feature branch, include the branch parameter
- Pull request analysis is available for PR-specific insights

## Common Troubleshooting

### Authentication Issues
- SonarQube requires USER tokens (not project tokens)
- When the error `SonarQube answered with Not authorized` occurs, verify the token type

### Project Not Found
- Use `search_my_sonarqube_projects` to confirm available projects
- Check if user has access to the specific project
- Verify project key spelling and format

### Code Analysis Issues
- Ensure programming language is correctly specified
- Remind users that snippet analysis doesn't replace full project scans
- Provide full file content for better analysis results
- Mention that code snippet analysis tool has limited capabilities compared to full SonarQube scans
