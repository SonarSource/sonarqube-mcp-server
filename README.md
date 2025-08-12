# SonarQube MCP Server

[![Build](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SonarSource_sonar-mcp-server&metric=alert_status&token=364a508a1e77096460f8571d8e66b41c99c95bea)](https://sonarcloud.io/summary/new_code?id=SonarSource_sonar-mcp-server)

The SonarQube MCP Server is a Model Context Protocol (MCP) server that enables seamless integration with SonarQube Server or Cloud for code quality and security.
It also supports the analysis of code snippet directly within the agent context.

## Quick setup

The simplest method is to rely on our Docker image hosted at [mcp/sonarqube](https://hub.docker.com/r/mcp/sonarqube). Read below for how to build locally.

### VS Code

You can use the following buttons to simplify the installation process within VS Code.

[![Install for SonarQube Cloud](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Cloud-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_ORG%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Key%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_ORG%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_ORG%22%3A%22%24%7Binput%3ASONARQUBE_ORG%7D%22%7D%7D)

[![Install for SonarQube Server](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Server-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20User%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_URL%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20URL%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_URL%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_URL%22%3A%22%24%7Binput%3ASONARQUBE_URL%7D%22%7D%7D)

### Cursor

* To connect with SonarQube Cloud:

[![Install for SonarQube Cloud](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/install-mcp?name=sonarqube&config=eyJjb21tYW5kIjoiZG9ja2VyIHJ1biAtaSAtLXJtIC1lIFNPTkFSUVVCRV9UT0tFTiAtZSBTT05BUlFVQkVfT1JHIG1jcC9zb25hcnF1YmUiLCJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiPHRva2VuPiIsIlNPTkFSUVVCRV9PUkciOiI8b3JnPiJ9fQ%3D%3D)

* To connect with SonarQube Server:

[![Install for SonarQube Server](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/install-mcp?name=sonarqube&config=eyJjb21tYW5kIjoiZG9ja2VyIHJ1biAtaSAtLXJtIC1lIFNPTkFSUVVCRV9UT0tFTiAtZSBTT05BUlFVQkVfVVJMIG1jcC9zb25hcnF1YmUiLCJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiPHRva2VuPiIsIlNPTkFSUVVCRV9VUkwiOiI8dXJsPiJ9fQ%3D%3D)

### Windsurf

SonarQube MCP Server is available as a Windsurf plugin. Follow these instructions:

1. Click on the `Plugins` button at the top right of the Cascade view
2. Search for `sonarqube` on the Plugin store
3. Click `Install`
4. Add the required SonarQube token. Then add the organization key if you want to connect with SonarQube Cloud, or the SonarQube URL if you want to connect to SonarQube Server or Community Build.

## Manual installation

You can manually install the SonarQube MCP server by copying the following snippet in the MCP servers configuration file:

* To connect with SonarQube Cloud:

```JSON
{
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
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_ORG": "<org>"
    }
  }
}
```

* To connect with SonarQube Server:

```JSON
{
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
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_URL": "<url>"
    }
  }
}
```

## Build

SonarQube MCP Server requires a Java Development Kit (JDK) version 21 or later to build.

Run the following Gradle command to clean the project and build the application:

```bash
./gradlew clean build -x test
```

The JAR file will be created in `build/libs/`.

You will then need to manually copy and paste the MCP configuration, as follows:

* To connect with SonarQube Cloud:

```JSON
{
  "sonarqube": {
    "command": "java",
    "args": [
      "-jar",
      "<path_to_sonarqube_mcp_server_jar>"
    ],
    "env": {
      "STORAGE_PATH": "<path_to_your_mcp_storage>",
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_ORG": "<org>"
    }
  }
}
```

* To connect with SonarQube Server:

```JSON
{
  "sonarqube": {
    "command": "java",
    "args": [
      "-jar",
      "<path_to_sonarqube_mcp_server_jar>"
    ],
    "env": {
      "STORAGE_PATH": "<path_to_your_mcp_storage>",
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_URL": "<url>"
    }
  }
}
```

## Configuration

Depending on your environment, you should provide specific environment variables.

### Mandatory

You should add the following variable when running the MCP Server:

| Environment variable | Description                                                                                                                               |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `STORAGE_PATH`       | An absolute path to a writable directory where SonarQube MCP Server will store its files (e.g., for creation, updates, and persistence) |

### SonarQube Cloud

To enable full functionality, the following environment variables must be set before starting the server:

| Environment variable | Description                                                                                                                               |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOKEN`    | Your SonarQube Cloud [token](https://docs.sonarsource.com/sonarqube-cloud/managing-your-account/managing-tokens/) |
| `SONARQUBE_ORG`      | Your SonarQube Cloud organization [key](https://sonarcloud.io/account/organizations)                                                      |

### SonarQube Server

| Environment variable | Description                                                                                                                                 |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOKEN`     | Your SonarQube Server **USER** [token](https://docs.sonarsource.com/sonarqube-server/latest/user-guide/managing-tokens/#generating-a-token) |
| `SONARQUBE_URL`       | Your SonarQube Server URL                                                                                                                   |

### Custom Certificates

If your SonarQube Server uses a self-signed certificate or a certificate from a private Certificate Authority (CA), you can add custom certificates to the Docker container that will automatically be installed.

#### Using Docker Volume Mount

Mount a directory containing your certificates when running the container:

```bash
docker run -i --rm \
  -v /path/to/your/certificates/:/usr/local/share/ca-certificates/:ro \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_URL="<url>" \
  mcp/sonarqube
```

#### Supported Certificate Formats

The container supports the following certificate formats:
- `.crt` files (PEM or DER encoded)
- `.pem` files (PEM encoded)

#### MCP Configuration with Certificates

When using custom certificates, you can modify your MCP configuration to mount the certificates:

```JSON
{
  "sonarqube": {
    "command": "docker",
    "args": [
      "run",
      "-i",
      "--rm",
      "-v",
      "/path/to/your/certificates/:/usr/local/share/ca-certificates/:ro",
      "-e",
      "SONARQUBE_TOKEN",
      "-e",
      "SONARQUBE_URL",
      "mcp/sonarqube"
    ],
    "env": {
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_URL": "<url>"
    }
  }
}
```

## Tools

### Analysis

- **analyze_code_snippet** - Analyze a code snippet with SonarQube analyzers to find SonarQube issues in it.
  - `codeSnippet` - Code snippet or full file content - _Required String_
  - `language` - Optional language of the code snippet - _String_

### Languages

- **list_languages** - List all programming languages supported in this instance
  - `q` - Optional pattern to match language keys/names against - _String_

### Issues

- **change_sonar_issue_status** - Change the status of a SonarQube issue to "accept", "falsepositive" or to "reopen" an issue
  - `key` - Issue key - _Required String_
  - `status` - New issue's status - _Required Enum {"accept", "falsepositive", "reopen"}_


- **search_sonar_issues_in_projects** - Search for SonarQube issues in my organization's projects
  - `projects` - Optional list of Sonar projects - _String[]_
  - `pullRequestId` - Optional Pull Request's identifier - _String_
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Measures

- **get_component_measures** - Get measures for a component (project, directory, file)
  - `component` - Optional component key to get measures for - _String_
  - `branch` - Optional branch to analyze for measures - _String_
  - `metricKeys` - Optional metric keys to retrieve (e.g. nloc, complexity, violations, coverage) - _String[]_
  - `pullRequest` - Optional pull request identifier to analyze for measures - _String_

### Metrics

- **search_metrics** - Search for metrics
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Projects

- **search_my_sonarqube_projects** - Find Sonar projects in my organization
  - `page` - Optional page number - _String_

### Quality Gates

- **get_project_quality_gate_status** - Get the Quality Gate Status for the project
  - `analysisId` - Optional analysis ID - _String_
  - `branch` - Optional branch key - _String_
  - `projectId` - Optional project ID - _String_
  - `projectKey` - Optional project key - _String_
  - `pullRequest` - Optional pull request ID - _String_


- **list_quality_gates** - List all quality gates in the organization

### Rules

- **list_rule_repositories** - List rule repositories available in SonarQube
  - `language` - Optional language key - _String_
  - `q` - Optional search query - _String_


- **show_rule** - Shows detailed information about a SonarQube rule
  - `key` - Rule key - _Required String_

### Sources

- **get_raw_source** - Get source code as raw text. Require 'See Source Code' permission on file
  - `key` - File key - _Required String_
  - `branch` - Optional branch key - _String_
  - `pullRequest` - Optional pull request id - _String_


- **get_scm_info** - Get SCM information of source files. Require See Source Code permission on file's project
  - `key` - File key - _Required String_
  - `commits_by_line` - Group lines by SCM commit if value is false, else display commits for each line - _String_
  - `from` - First line to return. Starts at 1 - _Number_
  - `to` - Last line to return (inclusive) - _Number_

### System

**Note: System tools are only available when connecting to SonarQube Server.**

- **get_system_health** - Get the health status of SonarQube Server instance


- **get_system_info** - Get detailed information about SonarQube Server system configuration including JVM state, database, search indexes, and settings. Requires 'Administer' permissions


- **get_system_logs** - Get SonarQube Server system logs in plain-text format. Requires system administration permission
  - `name` - Optional name of the logs to get. Possible values: access, app, ce, deprecation, es, web. Default: app - _String_


- **ping_system** - Ping the SonarQube Server system to check if it's alive


- **get_system_status** - Get state information about SonarQube Server

## Troubleshooting

Applications logs will be written to the `STORAGE_PATH/logs/mcp.log` file.

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/)
