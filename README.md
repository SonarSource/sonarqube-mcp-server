# SonarQube MCP Server

[![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonarqube-mcp-server.svg?branch=master)](https://cirrus-ci.com/github/SonarSource/sonarqube-mcp-server)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SonarSource_sonar-mcp-server&metric=alert_status&token=364a508a1e77096460f8571d8e66b41c99c95bea)](https://sonarcloud.io/summary/new_code?id=SonarSource_sonarqube-mcp-server)

The SonarQube MCP Server is a Model Context Protocol (MCP) server that provides seamless integration with SonarQube Server or Cloud.
It also enables the analysis of code snippet directly within the agent context.

## Prerequisites

SonarQube MCP Server can be launched in two ways:

* **With a Docker Container**
  * **Requires:** Docker installed.
* **Directly from the JAR**
  * **Requires:** Java Development Kit (JDK) version 21 or later.

## Installation

### Building

Run the following Gradle command to clean the project, download analyzers, and build the application:

```bash
./gradlew clean build -x test
```

The JAR file will be created in `build/libs/`.

To create the Docker image, run the `buildDocker` task:

```bash
./gradlew clean buildDocker -x test
```

### Usage

#### VS Code

Once the application is built locally (either as a JAR or a Docker image), you can use the following buttons to simplify the installation process within VS Code.

[![Install for SonarQube Cloud with Docker](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Cloud_with_Docker-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube-mcp-server&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_ORG%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Key%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22sonarqube-mcp-server%3A0.0.1-SNAPSHOT%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_ORG%22%3A%22%24%7Binput%3ASONARQUBE_ORG%7D%22%7D%7D)

[![Install for SonarQube Server with Docker](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Server_with_Docker-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube-mcp-server&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20User%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_URL%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20URL%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22sonarqube-mcp-server%3A0.0.1-SNAPSHOT%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_URL%22%3A%22%24%7Binput%3ASONARQUBE_URL%7D%22%7D%7D)

#### General cases

Alternatively, you can manually copy and paste the MCP configurations. Below are example snippets.

- Docker

```JSON
{
  "sonarqube-mcp-server-docker": {
    "command": "docker",
    "args": [
      "run",
      "-i",
      "--rm",
      "-e",
      "SONARQUBE_TOKEN",
      "-e",
      "SONARQUBE_ORG",
      "sonarqube-mcp-server:<version>"
    ],
    "env": {
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_ORG": "<org>"
    }
  }
}
```

- JAR

```JSON
{
  "sonarqube-mcp-server": {
    "command": "java",
    "args": [
      "-jar",
      "<path_to_sonarqube_mcp_server_jar>"
    ],
    "env": {
      "STORAGE_PATH": "<path_to_your_mcp_storage>",
      "PLUGINS_PATH": "<path_to_your_sonar_analyzers>",
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_ORG": "<org>"
    }
  }
}
```

## Configuration

Depending on your environment, you should provide specific environment variables.

### SonarQube Cloud

To enable full functionality, the following environment variables must be set before starting the server:

| Environmment variable | Description                                                                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOKEN`     | Your SonarQube Cloud [token](https://docs.sonarsource.com/sonarqube-cloud/managing-your-account/managing-tokens/) |
| `SONARQUBE_ORG`       | Your SonarQube Cloud organization [key](https://sonarcloud.io/account/organizations)                                                      |

### SonarQube Server

| Environmment variable | Description                                                                                                                                 |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOKEN`      | Your SonarQube Server **USER** [token](https://docs.sonarsource.com/sonarqube-server/latest/user-guide/managing-tokens/#generating-a-token) |
| `SONARQUBE_URL`        | Your SonarQube Server URL                                                                                                                   |

### JAR

On top of the previous SonarQube environments, you should add the following variable when running the MCP Server via a JAR:

| Environmment variable | Description                                                                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `STORAGE_PATH`        | An absolute path to a writable directory where SonarQube MCP Server will store its files (e.g., for creation, updates, and persistence) |
| `PLUGINS_PATH`        | An optional absolute path to a folder containing the SonarQube analyzers. If none is provided, analysis is disabled |

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

### Measures

- **get_component_measures** - Get measures for a component (project, directory, file)
  - `component` - Optional component key to get measures for - _String_
  - `branch` - Optional branch to analyze for measures - _String_
  - `metricKeys` - Optional metric keys to retrieve (e.g. nloc, complexity, violations, coverage) - _String[]_
  - `pullRequest` - Optional pull request identifier to analyze for measures - _String_

### Metrics

- **search_metrics** - Search for metrics
  - `p` - Optional 1-based page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Projects

- **search_my_sonarqube_cloud_projects** - Find Sonar projects in my organization
  - `page` - Optional page number - _String_

### Quality Gates

- **get_quality_gate_status_for_project** - Get the Quality Gate Status for the project
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

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/)
