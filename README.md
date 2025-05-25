# Sonar MCP Server


[![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonar-mcp-server.svg?branch=master)](https://cirrus-ci.com/github/SonarSource/sonar-mcp-server)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SonarSource_sonar-mcp-server&metric=alert_status&token=364a508a1e77096460f8571d8e66b41c99c95bea)](https://sonarcloud.io/summary/new_code?id=SonarSource_sonar-mcp-server)

The Sonar MCP Server is a Model Context Protocol (MCP) server that provides seamless integration with SonarQube Cloud.

## Prerequisites

Sonar MCP Server can be launched in two ways:

* **Docker Container**
  * **Requires:** Docker installed.
* **Directly from JAR**
  * **Requires:** Java Development Kit (JDK) version 21 or later.

### Configuration

To enable full functionality, the following environment variables must be set before starting the server:

* `SONARQUBE_CLOUD_TOKEN`: Your SonarQube Cloud **USER** [token](https://sonarcloud.io/account/security).
* `SONARQUBE_CLOUD_ORG`: Your SonarQube Cloud organization [key](https://sonarcloud.io/account/organizations).
* `STORAGE_PATH`: An absolute path to a writable directory where Sonar MCP Server will store its files (e.g., for creation, updates, and persistence).
  * *This variable is not required when running within a Docker container, as bind mounts are used for storage.*

## Installation

### Building

Run the following Gradle command to clean the project, and build the application:

```bash
./gradlew clean build -x test
```

The JAR file will be created in `build/libs/`

To create the Docker image:

```bash
./gradlew clean build buildDocker -x test
```

### Usage with VS Code

Once the application is built locally (either as a JAR or a Docker image), you can use the following buttons to simplify the installation process within VS Code.

[![Install with Docker in VS Code](https://img.shields.io/badge/VS_Code-Install_Docker_Sonar_MCP_Server-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonar-mcp-server&inputs=%5B%7B%22id%22%3A%22sonarqube_cloud_token%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20USER%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22sonarqube_cloud_organization%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Name%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22sonar-mcp-server%3A0.0.1-SNAPSHOT%22%5D%2C%22env%22%3A%7B%22SONARQUBE_CLOUD_TOKEN%22%3A%22%24%7Binput%3Asonarqube_cloud_token%7D%22%2C%22SONARQUBE_CLOUD_ORG%22%3A%22%24%7Binput%3Asonarqube_cloud_organization%7D%22%7D%7D)

[![Install with JAR in VS Code](https://img.shields.io/badge/VS_Code-Install_JAR_Sonar_MCP_Server-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonar-mcp-server&inputs=%5B%7B%22id%22%3A%22storage_path%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22Storage%20Path%22%2C%22password%22%3Afalse%7D%2C%7B%22id%22%3A%22sonarqube_cloud_token%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20USER%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22sonarqube_cloud_organization%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Name%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22java%22%2C%22args%22%3A%5B%22-jar%22%2C%22%3Cpath_to_sonar_mcp_server_jar%3E%22%5D%2C%22env%22%3A%7B%22STORAGE_PATH%22%3A%22%24%7Binput%3Astorage_path%7D%22%2C%22SONARQUBE_CLOUD_TOKEN%22%3A%22%24%7Binput%3Asonarqube_cloud_token%7D%22%2C%22SONARQUBE_CLOUD_ORG%22%3A%22%24%7Binput%3Asonarqube_cloud_organization%7D%22%7D%7D)

Alternatively, you can manually create or update your VS Code MCP configurations. Below are example snippets.

#### Docker

```JSON
{
  "sonar-mcp-server-docker": {
    "command": "docker",
    "args": [
      "run",
      "-i",
      "--rm",
      "sonar-mcp-server:<version>"
    ],
    "env": {
      "SONARQUBE_CLOUD_TOKEN": "<token>",
      "SONARQUBE_CLOUD_ORG": "<org>"
    }
  }
}
```

#### JAR

```JSON
{
  "sonar-mcp-server": {
    "command": "java",
    "args": [
      "-jar",
      "<path_to_sonar_mcp_server_jar>"
    ],
    "env": {
      "STORAGE_PATH": "<path_to_your_mcp_storage>",
      "SONARQUBE_CLOUD_TOKEN": "<sonarqube_cloud_user_token>",
      "SONARQUBE_CLOUD_ORG": "<sonarqube_cloud_organization>"
    }
  }
}
```

## Tools

### Badges

- **get_project_measure_badge** - Find Sonar projects in my organization
  - `project` - Project or application key - _Required String_
  - `metric` - Metric key (e.g. 'alert_status', 'bugs', etc) - _Required String_
  - `branch` - Optional branch key - _String_
  - `token` - Optional security token required for private projects - _String_


- **get_ai_code_assurance_badge** - Find Sonar projects in my organization
  - `project` - Project or application key - _Required String_
  - `token` - Optional security token required for private projects - _String_


- **get_quality_gate_badge** - Get an SVG badge showing the quality gate status for a project
  - `project` - Project or application key - _Required String_
  - `branch` - Optional branch key - _String_
  - `token` - Optional security token required for private projects - _String_

### Languages

- **list_languages** - List all programming languages supported in this instance
  - `q` - Optional pattern to match language keys/names against - _String_

### Issues

- **change_sonar_issue_status** - Change the status of a Sonar issue to "accept", "falsepositive" or to "reopen" an issue
  - `key` - Issue key - _Required String_
  - `status` - New issue's status - _Required Enum {"accept", "falsepositive", "reopen"}_


- **search_sonar_issues_in_projects** - Search for Sonar issues in my organization's projects
  - `projects` - Optional list of Sonar projects - _String[]_
  - `pullRequestId` - Optional Pull Request's identifier - _String_

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
