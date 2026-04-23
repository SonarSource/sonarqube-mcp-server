# SonarQube MCP Server

[![Build](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SonarSource_sonarqube-mcp-server&metric=alert_status&token=364a508a1e77096460f8571d8e66b41c99c95bea)](https://sonarcloud.io/summary/new_code?id=SonarSource_sonarqube-mcp-server)

The SonarQube MCP Server is a Model Context Protocol (MCP) server that enables seamless integration with SonarQube Server or Cloud for code quality and security.
It also supports the analysis of code snippet directly within the agent context.

## Quick setup

<details>
<summary>Security best practices</summary>

> 🔒 **Important**: Your SonarQube token is a sensitive credential. Follow these security practices:

**When using CLI commands:**
- **Avoid hardcoding tokens** in command-line arguments – they get saved in shell history
- **Use environment variables** – set tokens in environment variables before running commands

**When using configuration files:**
- **Never commit tokens** to version control
- **Use environment variable substitution** in config files when possible

</details>

### 🚀 Generate your configuration

The fastest way to get started is the **[SonarQube MCP Server Configuration Generator](https://mcp.sonarqube.com/config-generator.html)** – an interactive tool that produces a ready-to-use configuration for your preferred AI agent client.

### Manual setup

If you prefer to configure things yourself, the simplest method is to use our container image hosted at [mcp/sonarqube](https://hub.docker.com/r/mcp/sonarqube). Read below if you want to build it locally.

> **Note:** While the examples below use `docker`, any OCI-compatible container runtime works (e.g., Podman, nerdctl). Simply replace `docker` with your preferred tool.

<details>
<summary>Antigravity</summary>

SonarQube MCP Server is available in the Antigravity MCP Store. Follow these instructions:

1. Open the **Agent Side Panel**
2. Click the three dots (**...**) at the top right and select **MCP Servers**
3. Search for `SonarQube` and select **Install**
4. Provide the required SonarQube User token. You can also provide your organization key for SonarQube Cloud or the SonarQube URL if connecting to SonarQube Server.

For **SonarQube Cloud US**, set the URL to `https://sonarqube.us`.

Alternatively, you can manually configure the server via `mcp_config.json`:

* To connect with SonarQube Cloud:

In the Agent Side Panel, click the three dots (**...**) -> **MCP Store** -> **Manage MCP Servers** -> **View raw config**, and add the following:

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": ["run", "--init", "--pull=always", "-i", "--rm", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_ORG", "mcp/sonarqube"],
      "env": {
        "SONARQUBE_TOKEN": "<YOUR_TOKEN>",
        "SONARQUBE_ORG": "<YOUR_ORG>"
      }
    }
  }
}
```

For **SonarQube Cloud US**, manually add `"SONARQUBE_URL": "https://sonarqube.us"` to the `env` section and `"-e", "SONARQUBE_URL"` to the `args` array.

* To connect with SonarQube Server:

```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": ["run", "--init", "--pull=always", "-i", "--rm", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_URL", "mcp/sonarqube"],
      "env": {
        "SONARQUBE_TOKEN": "<YOUR_USER_TOKEN>",
        "SONARQUBE_URL": "<YOUR_SERVER_URL>"
      }
    }
  }
}
```

</details>

<details>
<summary>Claude Code</summary>

* To connect with SonarQube Cloud:

```bash
claude mcp add sonarqube \
  --env SONARQUBE_TOKEN=$SONAR_TOKEN \
  --env SONARQUBE_ORG=$SONAR_ORG \
  -- docker run --init --pull=always -i --rm -e SONARQUBE_TOKEN -e SONARQUBE_ORG mcp/sonarqube
```

For **SonarQube Cloud US**, add `--env SONARQUBE_URL=https://sonarqube.us` to the command.

* To connect with SonarQube Server:

```bash
claude mcp add sonarqube \
  --env SONARQUBE_TOKEN=$SONAR_USER_TOKEN \
  --env SONARQUBE_URL=$SONAR_URL \
  -- docker run --init --pull=always -i --rm -e SONARQUBE_TOKEN -e SONARQUBE_URL mcp/sonarqube
```

</details>

<details>
<summary>Codex CLI</summary>

Manually edit the configuration file at `~/.codex/config.toml` and add the following configuration:

* To connect with SonarQube Cloud:

```toml
[mcp_servers.sonarqube]
command = "docker"
args = ["run", "--init", "--pull=always", "--rm", "-i", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_ORG", "mcp/sonarqube"]
env = { "SONARQUBE_TOKEN" = "<YOUR_USER_TOKEN>", "SONARQUBE_ORG" = "<YOUR_ORG>" }
```

For **SonarQube Cloud US**, add `"SONARQUBE_URL" = "https://sonarqube.us"` to the `env` section and `"-e", "SONARQUBE_URL"` to the `args` array.

* To connect with SonarQube Server:

```toml
[mcp_servers.sonarqube]
command = "docker"
args = ["run", "--init", "--pull=always", "--rm", "-i", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_URL", "mcp/sonarqube"]
env = { "SONARQUBE_TOKEN" = "<YOUR_TOKEN>", "SONARQUBE_URL" = "<YOUR_SERVER_URL>" }
```

</details>

<details>
<summary>Cursor</summary>

* To connect with SonarQube Cloud:

[![Install for SonarQube Cloud](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/en-US/install-mcp?name=sonarqube&config=eyJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiWU9VUl9UT0tFTiIsIlNPTkFSUVVCRV9PUkciOiJZT1VSX1NPTkFSUVVCRV9PUkcifSwiY29tbWFuZCI6ImRvY2tlciBydW4gLS1pbml0IC0tcHVsbD1hbHdheXMgLWkgLS1ybSAtZSBTT05BUlFVQkVfVE9LRU4gLWUgU09OQVJRVUJFX09SRyBtY3Avc29uYXJxdWJlIn0%3D)

For **SonarQube Cloud US**, manually add `"SONARQUBE_URL": "https://sonarqube.us"` to the `env` section in your MCP configuration after installation.

* To connect with SonarQube Server:

[![Install for SonarQube Server](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/en-US/install-mcp?name=sonarqube&config=eyJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiWU9VUl9VU0VSX1RPS0VOIiwiU09OQVJRVUJFX1VSTCI6IllPVVJfU09OQVJRVUJFX1VSTCJ9LCJjb21tYW5kIjoiZG9ja2VyIHJ1biAtLWluaXQgLS1wdWxsPWFsd2F5cyAtaSAtLXJtIC1lIFNPTkFSUVVCRV9UT0tFTiAtZSBTT05BUlFVQkVfVVJMIG1jcC9zb25hcnF1YmUifQ%3D%3D)

</details>

<details>
<summary>Gemini CLI</summary>

You can install our MCP server extension by using the following command:

```bash
gemini extensions install https://github.com/SonarSource/sonarqube-mcp-server
```

You will need to set the required environment variables before starting Gemini:

**Environment Variables Required:**

* **For SonarQube Cloud:**
  - `SONARQUBE_TOKEN` - Your SonarQube Cloud token
  - `SONARQUBE_ORG` - Your organization key
  - `SONARQUBE_URL` - (Optional) Set to `https://sonarqube.us` for SonarQube Cloud US

* **For SonarQube Server:**
  - `SONARQUBE_TOKEN` - Your SonarQube Server USER token
  - `SONARQUBE_URL` - Your SonarQube Server URL

Once installed, the extension will be installed under `<home>/.gemini/extensions/sonarqube-mcp-server/gemini-extension.json`.

</details>

<details>
<summary>GitHub Copilot CLI</summary>

After starting Copilot CLI, run the following command to add the SonarQube MCP server:

```bash
/mcp add
```

You will have to provide different information about the MCP server, you can use tab to navigate between fields.

* To connect with SonarQube Cloud:

```
Server Name: sonarqube
Server Type: Local (Press 1)
Command: docker
Arguments: run, --init, --pull=always, --rm, -i, -e, SONARQUBE_TOKEN, -e, SONARQUBE_ORG, mcp/sonarqube
Environment Variables: SONARQUBE_TOKEN=<YOUR_TOKEN>,SONARQUBE_ORG=<YOUR_ORG>
Tools: *
```

For **SonarQube Cloud US**, add `-e, SONARQUBE_URL` to Arguments and `SONARQUBE_URL=https://sonarqube.us` to Environment Variables.

* To connect with SonarQube Server:

```
Server Name: sonarqube
Server Type: Local (Press 1)
Command: docker
Arguments: run, --init, --pull=always, --rm, -i, -e, SONARQUBE_TOKEN, -e, SONARQUBE_URL, mcp/sonarqube
Environment Variables: SONARQUBE_TOKEN=<YOUR_USER_TOKEN>,SONARQUBE_URL=<YOUR_SERVER_URL>
Tools: *
```

The configuration file is located at `~/.copilot/mcp-config.json`.

</details>

<details>
<summary>GitHub Copilot coding agent</summary>

GitHub Copilot coding agent can leverage the SonarQube MCP server directly in your CI/CD.

To add the secrets to your Copilot environment, follow the Copilot [documentation](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/coding-agent/extend-coding-agent-with-mcp#setting-up-a-copilot-environment-for-copilot-coding-agent). Only secrets with names prefixed with **COPILOT_MCP_** will be available to your MCP configuration.

In your GitHub repository, navigate under **Settings -> Copilot -> Coding agent**, and add the following configuration in the MCP configuration section:

* To connect with SonarQube Cloud:

```
{
  "mcpServers": {
    "sonarqube": {
      "type": "local",
      "command": "docker",
      "args": [
        "run",
        "--init",
        "--pull=always",
        "--rm",
        "-i",
        "-e",
        "SONARQUBE_TOKEN=$SONAR_TOKEN",
        "-e",
        "SONARQUBE_ORG=$SONAR_ORG",
        "mcp/sonarqube"
      ],
      "env": {
        "SONAR_TOKEN": "COPILOT_MCP_SONARQUBE_TOKEN",
        "SONAR_ORG": "COPILOT_MCP_SONARQUBE_ORG"
      },
      "tools": ["*"]
    }
  }
}
```

For **SonarQube Cloud US**, add `"-e", "SONARQUBE_URL=$SONAR_URL"` to the `args` array and `"SONAR_URL": "COPILOT_MCP_SONARQUBE_URL"` to the `env` section, then set the secret `COPILOT_MCP_SONARQUBE_URL=https://sonarqube.us`.

* To connect with SonarQube Server:

```
{
  "mcpServers": {
    "sonarqube": {
      "type": "local",
      "command": "docker",
      "args": [
        "run",
        "--init",
        "--pull=always",
        "--rm",
        "-i",
        "-e",
        "SONARQUBE_TOKEN=$SONAR_TOKEN",
        "-e",
        "SONARQUBE_URL=$SONAR_URL",
        "mcp/sonarqube"
      ],
      "env": {
        "SONAR_TOKEN": "COPILOT_MCP_SONARQUBE_USER_TOKEN",
        "SONAR_URL": "COPILOT_MCP_SONARQUBE_URL"
      },
      "tools": ["*"]
    }
  }
}
```

</details>

<details>
<summary>Kiro</summary>

[![Add to Kiro](https://kiro.dev/images/add-to-kiro.svg)](https://kiro.dev/launch/mcp/add?name=sonarqube&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22--init%22%2C%22--pull%3Dalways%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_ORG%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22YOUR_TOKEN%22%2C%22SONARQUBE_ORG%22%3A%22YOUR_ORG%22%7D%7D)

Create a `.kiro/settings/mcp.json` file in your workspace directory (or edit if it already exists), add the following configuration:

* To connect with SonarQube Cloud:

```
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": [
        "run",
        "--init",
        "--pull=always",
        "-i",
        "--rm",
        "-e", 
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_ORG",
        "mcp/sonarqube"
      ],
      "env": {
        "SONARQUBE_TOKEN": "<YOUR_TOKEN>",
        "SONARQUBE_ORG": "<YOUR_ORG>"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

For **SonarQube Cloud US**, add `"-e", "SONARQUBE_URL"` to the `args` array and `"SONARQUBE_URL": "https://sonarqube.us"` to the `env` section.

* To connect with SonarQube Server:

```
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": [
        "run",
        "--init",
        "--pull=always",
        "-i",
        "--rm",
        "-e", 
        "SONARQUBE_TOKEN",
        "-e",
        "SONARQUBE_URL",
        "mcp/sonarqube"
      ],
      "env": {
        "SONARQUBE_TOKEN": "<YOUR_USER_TOKEN>",
        "SONARQUBE_URL": "<YOUR_SERVER_URL>"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

</details>
    
<details>
<summary>VS Code</summary>

You can use the following buttons to simplify the installation process within VS Code.

[![Install for SonarQube Cloud](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Cloud-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_ORG%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Key%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22--init%22%2C%22--pull%3Dalways%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_ORG%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_ORG%22%3A%22%24%7Binput%3ASONARQUBE_ORG%7D%22%7D%7D)

For **SonarQube Cloud US**, manually add `"SONARQUBE_URL": "https://sonarqube.us"` to the `env` section in your MCP configuration after installation.

[![Install for SonarQube Server](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Server-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20User%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_URL%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20URL%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22--init%22%2C%22--pull%3Dalways%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_URL%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_URL%22%3A%22%24%7Binput%3ASONARQUBE_URL%7D%22%7D%7D)

</details>
<details>
<summary>Windsurf</summary>

SonarQube MCP Server is available as a Windsurf plugin. Follow these instructions:

1. Open Windsurf **Settings** > **Cascade** > **MCP Servers** and select **Open MCP Marketplace** 
2. Search for `sonarqube` on the Cascade MCP Marketplace
3. Choose the **SonarQube MCP Server** and select **Install**
4. Add the required SonarQube User token. Then add the organization key if you want to connect with SonarQube Cloud, or the SonarQube URL if you want to connect to SonarQube Server or Community Build.

For **SonarQube Cloud US**, set the URL to `https://sonarqube.us`.

</details>

<details>
<summary>Zed</summary>

Navigate to the **Extensions** view in Zed and search for **SonarQube MCP Server**.
When installing the extension, you will be prompted to provide the necessary environment variables:

* When using SonarQube Cloud:

```
{
  "sonarqube_token": "YOUR_SONARQUBE_TOKEN",
  "sonarqube_org": "SONARQUBE_ORGANIZATION_KEY",
  "docker_path": "DOCKER_PATH"
}
```

For **SonarQube Cloud US**, add `"sonarqube_url": "https://sonarqube.us"` to the configuration.

* When using SonarQube Server:

```
{
  "sonarqube_token": "YOUR_SONARQUBE_USER_TOKEN",
  "sonarqube_url": "YOUR_SONARQUBE_SERVER_URL",
  "docker_path": "DOCKER_PATH"
}
```

The `docker_path` is the path to a docker executable. Examples:

Linux/macOS: `/usr/bin/docker` or `/usr/local/bin/docker`

Windows: `C:\Program Files\Docker\Docker\resources\bin\docker.exe`

</details>

> 💡 **Tip:** We recommend pulling the latest image regularly or before reporting issues to ensure you have the most up-to-date features and fixes.

## Manual installation

You can manually install the SonarQube MCP server by copying the following snippet in the MCP servers configuration file:

* To connect with SonarQube Cloud:

```JSON
{
  "sonarqube": {
    "command": "docker",
    "args": [
      "run",
      "--init",
      "--pull=always",
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
      "--init",
      "--pull=always",
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

## Integration with SonarQube for IDE

The SonarQube MCP Server can integrate with [SonarQube for IDE](https://www.sonarsource.com/products/sonarlint/) to further enhance your development workflow, providing better code analysis and insights directly within your IDE.

<details>
<summary>Configuration</summary>

When using SonarQube for IDE, the `SONARQUBE_IDE_PORT` environment variable should be set with the correct port number. SonarQube for VS Code includes a Quick Install button, which automatically sets the correct port configuration.

For example, with SonarQube Cloud:

```JSON
{
  "sonarqube": {
    "command": "docker",
    "args": [
      "run",
      "--init",
      "--pull=always",
      "-i",
      "--rm",
      "-e",
      "SONARQUBE_TOKEN",
      "-e",
      "SONARQUBE_ORG",
      "-e",
      "SONARQUBE_IDE_PORT",
      "mcp/sonarqube"
    ],
    "env": {
      "SONARQUBE_TOKEN": "<token>",
      "SONARQUBE_ORG": "<org>",
      "SONARQUBE_IDE_PORT": "<64120-64130>"
    }
  }
}
```

> When running the MCP server in a container on Linux, the container cannot access the SonarQube for IDE embedded server running on localhost. To allow the container to connect to the SonarQube for IDE server, add the `--network=host` option to your container run command.

</details>

## Configuration

Depending on your environment, you should provide specific environment variables.

### Base

You should add the following variable when running the MCP Server:

| Environment variable             | Description                                                                                                                                                                                                                 |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `STORAGE_PATH`                   | Mandatory absolute path to a writable directory where SonarQube MCP Server will store its files (e.g., for creation, updates, and persistence), it is automatically provided when using the container image                 |
| `SONARQUBE_PROJECT_KEY`          | Optional default project key. When set, all tools that require a project key will use this value automatically — the `projectKey` parameter is removed from their schema entirely. Useful when working on a single project. |
| `SONARQUBE_IDE_PORT`             | Optional port number between 64120 and 64130 used to connect SonarQube MCP Server with SonarQube for IDE.                                                                                                                   |
| `SONARQUBE_DEBUG_ENABLED`        | When set to `true`, enables debug logging. Debug logs are written to both the log file and STDERR. Useful for troubleshooting connectivity or configuration issues. Default: `false`.                                       |
| `SONARQUBE_LOG_TO_FILE_DISABLED` | When set to `true`, disables writing logs to disk entirely. No log files will be created under `STORAGE_PATH/logs/`. Useful in containerized or ephemeral environments where file logging is undesirable. Default: `false`. |

### Workspace Mount (Reducing Context Bloat)

By default, analysis tool `analyze_code_snippet` requires the agent to pass the full file content as a `fileContent` argument. For large files or when analyzing many files in a session, this significantly increases context window usage and cost.

**Solution:** mount your project directory into the container at `/app/mcp-workspace`. When this mount is detected, the server reads files directly from disk using the project-relative `filePath` argument — file content never passes through the agent context.

```json
{
  "args": [
    "run", "-i", "--rm", "--init", "--pull=always",
    "-e", "SONARQUBE_TOKEN",
    "-e", "SONARQUBE_ORG",
    "-v", "/path/to/your/project:/app/mcp-workspace",
    "mcp/sonarqube"
  ]
}
```

When the mount is active:
- `run_advanced_code_analysis` becomes available if your organization is entitled to it
- `analyze_code_snippet`: `filePath` is required and `fileContent` is not used — the server resolves the file the same way

### Selective Tool Enablement

By default, only important toolsets are enabled to reduce context overhead. You can enable additional toolsets as needed.

| Environment variable   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOOLSETS`   | Comma-separated list of toolsets to enable. When set, only these toolsets will be available. If not set, default important toolsets are enabled (`analysis`, `issues`, `projects`, `quality-gates`, `rules`, `duplications`, `measures`, `security-hotspots`, `dependency-risks`, `coverage`, `cag`). **Note:** The `projects` toolset is always enabled as it's required to find project keys for other operations. Context Augmentation tools are only available in stdio mode and require organization entitlement. In HTTP(S) mode, clients can send a `SONARQUBE_TOOLSETS` HTTP header to narrow this further per-request, but cannot enable toolsets beyond what the server was launched with (see [HTTP/HTTPS Transport](#2-http) below). |
| `SONARQUBE_READ_ONLY`  | When set to `true`, enables read-only mode which disables all write operations (changing issue status for example). This filter is cumulative with `SONARQUBE_TOOLSETS` if both are set. Default: `false`. In HTTP(S) mode, clients can send a `SONARQUBE_READ_ONLY` HTTP header to further restrict individual requests to read-only, but cannot lift a server-level read-only restriction (see [HTTP/HTTPS Transport](#2-http) below).                                                                                                                                                                                                                                                                                                         |

<details>
<summary>Available Toolsets</summary>

| Toolset                  | Key                 | Description                                                              |
|--------------------------|---------------------|--------------------------------------------------------------------------|
| **Analysis**             | `analysis`          | Code analysis tools (local analysis and advanced remote analysis)        |
| **Issues**               | `issues`            | Search and manage SonarQube issues                                       |
| **Security Hotspots**    | `security-hotspots` | Search and review Security Hotspots                                      |
| **Projects**             | `projects`          | Browse and search SonarQube projects                                     |
| **Quality Gates**        | `quality-gates`     | Access quality gates and their status                                    |
| **Rules**                | `rules`             | Browse and search SonarQube rules                                        |
| **Sources**              | `sources`           | Access source code and SCM information                                   |
| **Duplications**         | `duplications`      | Find code duplications across projects                                   |
| **Measures**             | `measures`          | Retrieve metrics and measures (includes both measures and metrics tools) |
| **Languages**            | `languages`         | List supported programming languages                                     |
| **Portfolios**           | `portfolios`        | Manage portfolios and enterprises (Cloud and Server)                     |
| **System**               | `system`            | System administration tools (Server only)                                |
| **Webhooks**             | `webhooks`          | Manage webhooks                                                          |
| **Dependency Risks**     | `dependency-risks`  | Analyze dependency risks and security issues (SCA)                       |
| **Coverage**             | `coverage`          | Test coverage analysis and improvement tools                             |
| **Context Augmentation** | `cag`               | Context Augmentation tools (stdio mode only, requires org entitlement)   |

#### Examples

**Enable analysis, issues, and quality gates toolsets (using Docker with SonarQube Cloud):**

```bash
docker run --init --pull=always -i --rm \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  -e SONARQUBE_TOOLSETS="analysis,issues,quality-gates" \
  mcp/sonarqube
```

Note: The `projects` toolset is always enabled automatically, so you don't need to include it in `SONARQUBE_TOOLSETS`.

**Enable read-only mode (using Docker with SonarQube Cloud):**

```bash
docker run --init --pull=always -i --rm \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  -e SONARQUBE_READ_ONLY="true" \
  mcp/sonarqube
```

</details>

#### SonarQube Cloud

To enable full functionality, the following environment variables must be set before starting the server:

| Environment variable  | Description                                                                                                               | Required |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------|----------|
| `SONARQUBE_TOKEN`     | Your SonarQube Cloud [token](https://docs.sonarsource.com/sonarqube-cloud/managing-your-account/managing-tokens/)         | Yes      |
| `SONARQUBE_ORG`       | Your SonarQube Cloud organization [key](https://sonarcloud.io/account/organizations)                                      | Yes      |
| `SONARQUBE_URL`       | Custom SonarQube Cloud URL (defaults to `https://sonarcloud.io`). Use this for SonarQube Cloud US: `https://sonarqube.us` | No       |

**Examples:**

- **SonarQube Cloud**: Only `SONARQUBE_TOKEN` and `SONARQUBE_ORG` are needed
- **SonarQube Cloud US**: Set `SONARQUBE_TOKEN`, `SONARQUBE_ORG`, and `SONARQUBE_URL=https://sonarqube.us`

#### SonarQube Server

| Environment variable | Description                                                                                                                                 | Required |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------|----------|
| `SONARQUBE_TOKEN`     | Your SonarQube Server **USER** [token](https://docs.sonarsource.com/sonarqube-server/latest/user-guide/managing-tokens/#generating-a-token) | Yes      |
| `SONARQUBE_URL`       | Your SonarQube Server URL                                                                                                                   | Yes      |

> ⚠️ Connection to SonarQube Server requires a token of type **USER** and will not function properly if project tokens or global tokens are used.

> 💡 **Configuration Tip (stdio mode)**: The presence of `SONARQUBE_ORG` determines whether you're connecting to SonarQube Cloud or Server. If `SONARQUBE_ORG` is set, SonarQube Cloud is used; otherwise, SonarQube Server is used.

### Transport Modes

The SonarQube MCP Server supports three transport modes:

#### 1. **Stdio** (Default - Recommended for Local Development)
The recommended mode for local development and single-user setups, used by all MCP clients.

**Example - Docker with SonarQube Cloud:**
```json
{
  "mcpServers": {
    "sonarqube": {
      "command": "docker",
      "args": ["run", "--init", "--pull=always", "-i", "--rm", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_ORG", "mcp/sonarqube"],
      "env": {
        "SONARQUBE_TOKEN": "<your-token>",
        "SONARQUBE_ORG": "<your-org>"
      }
    }
  }
}
```

#### 2. **HTTP**
Unencrypted HTTP transport. Use HTTPS instead for multi-user deployments.

> ⚠️ **Not Recommended:** Use **Stdio** for local development or **HTTPS** for multi-user production deployments.

| Environment variable | Description                                                      | Default         |
|----------------------|------------------------------------------------------------------|-----------------|
| `SONARQUBE_TRANSPORT`| Set to `http` to enable HTTP transport                           | Not set (stdio) |
| `SONARQUBE_HTTP_PORT`| Port number (1024-65535)                                         | `8080`          |
| `SONARQUBE_HTTP_HOST`| Host to bind (defaults to localhost for security)                | `127.0.0.1`     |

**Note:** In HTTP(S) mode, the server is stateless — each client request must include an `Authorization: Bearer <token>` header carrying the user's own SonarQube token. For SonarQube Cloud, the organization is resolved as follows:
- If `SONARQUBE_ORG` is set at server startup, all requests are routed to that organization. Clients must **not** send a `SONARQUBE_ORG` header — doing so will result in an error.
- If `SONARQUBE_ORG` is not set at server startup, each client **must** supply a `SONARQUBE_ORG` header on every request.
Clients can also narrow the visible tools per-request by supplying `SONARQUBE_TOOLSETS` and/or `SONARQUBE_READ_ONLY` headers; these apply additional filtering on top of the server-level configuration — they can only reduce the scope, never expand it.
No session state is maintained between requests.

> **Deprecated:** The `SONARQUBE_TOKEN` request header is still accepted for backward compatibility but will be removed in a future version. Migrate to `Authorization: Bearer <token>`.

#### 3. **HTTPS** (Recommended for Multi-User Production Deployments)
Secure multi-user transport with TLS encryption. Requires SSL certificates.

> ✅ **Recommended for Production:** Use HTTPS when deploying the MCP server for multiple users. The server binds to `127.0.0.1` (localhost) by default for security.

| Environment variable | Description                                                      | Default         |
|----------------------|------------------------------------------------------------------|-----------------|
| `SONARQUBE_TRANSPORT`| Set to `https` to enable HTTPS transport                         | Not set (stdio) |
| `SONARQUBE_HTTP_PORT`| Port number (typically 8443 for HTTPS)                          | `8080`          |
| `SONARQUBE_HTTP_HOST`| Host to bind (defaults to localhost for security)               | `127.0.0.1`     |

**SSL Certificate Configuration (Optional):**

| Environment variable | Description                                    | Default                     |
|----------------------|------------------------------------------------|-----------------------------|
| `SONARQUBE_HTTPS_KEYSTORE_PATH`    | Path to keystore file (.p12 or .jks) | `/etc/ssl/mcp/keystore.p12` |
| `SONARQUBE_HTTPS_KEYSTORE_PASSWORD`| Keystore password                     | `sonarlint`                 |
| `SONARQUBE_HTTPS_KEYSTORE_TYPE`    | Keystore type (PKCS12 or JKS)        | `PKCS12`                    |

**Example - Docker with SonarQube Cloud:**
```bash
docker run --init --pull=always -p 8443:8443 \
  -v $(pwd)/keystore.p12:/etc/ssl/mcp/keystore.p12:ro \
  -e SONARQUBE_TRANSPORT=https \
  -e SONARQUBE_HTTP_PORT=8443 \
  -e SONARQUBE_TOKEN="<init-token>" \
  -e SONARQUBE_ORG="<your-org>" \
  mcp/sonarqube
```

**Client Configuration (SonarQube Cloud):**
```json
{
  "mcpServers": {
    "sonarqube-https": {
      "url": "https://your-server:8443/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>",
        "SONARQUBE_ORG": "<your-org>",
        "SONARQUBE_TOOLSETS": "issues,quality-gates",
        "SONARQUBE_READ_ONLY": "true"
      }
    }
  }
}
```

**Client Configuration (SonarQube Server):**
```json
{
  "mcpServers": {
    "sonarqube-https": {
      "url": "https://your-server:8443/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>",
        "SONARQUBE_TOOLSETS": "issues,quality-gates",
        "SONARQUBE_READ_ONLY": "true"
      }
    }
  }
}
```

> **Note:** `SONARQUBE_TOOLSETS` and `SONARQUBE_READ_ONLY` are optional per-request headers that narrow the server-level tool set for that specific request. They can only reduce scope — they cannot enable toolsets or lift restrictions beyond what the server was launched with.

**Note:** For local development, use Stdio transport instead (the default). HTTPS is intended for multi-user production deployments with proper SSL certificates.

#### Service Endpoints

When running in **HTTP** or **HTTPS** transport mode, the server exposes a few unauthenticated service endpoints in addition to the MCP endpoint at `/mcp`. These are intended for service-to-service use (monitoring, orchestration, client compatibility checks) and do not require an `Authorization` header.

| Endpoint  | Method | Description                                                                                   | Example response                  |
|-----------|--------|-----------------------------------------------------------------------------------------------|-----------------------------------|
| `/health` | `GET`  | Liveness probe. Returns `200 OK` with an empty body once the server is accepting requests.    | *(empty body)*                    |
| `/info`   | `GET`  | Returns the MCP server version as JSON. Useful for verifying the deployed server version.     | `{"version":"1.16.0"}`            |

These endpoints are not available when running with the **Stdio** transport.

### Custom Certificates

If your SonarQube Server uses a self-signed certificate or a certificate from a private Certificate Authority (CA), you can add custom certificates to the container that will automatically be installed.

<details>
<summary>Configuration</summary>

#### Using Volume Mount

Mount a directory containing your certificates when running the container:

```bash
docker run --init --pull=always -i --rm \
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
      "--init",
      "--pull=always",
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

</details>

### Proxy

The SonarQube MCP Server supports HTTP and SOCKS5 proxies through standard Java proxy system properties.

<details>
<summary>Configuration</summary>

#### HTTP/HTTPS Proxy

You can configure proxy settings using Java system properties. These can be set as environment variables or passed as JVM arguments.

**Common Proxy Properties:**

| Property | Description | Example |
|----------|-------------|---------|
| `http.proxyHost` | HTTP proxy hostname | `proxy.example.com` |
| `http.proxyPort` | HTTP proxy port | `8080` |
| `https.proxyHost` | HTTPS proxy hostname | `proxy.example.com` |
| `https.proxyPort` | HTTPS proxy port | `8443` |
| `http.nonProxyHosts` | Hosts that bypass the proxy (pipe-separated) | `localhost\|127.0.0.1\|*.internal.com` |

**HTTP/HTTPS Proxy Authentication:**

| Property | Description | Example |
|----------|-------------|---------|
| `http.proxyUser` | HTTP proxy username | `myuser` |
| `http.proxyPassword` | HTTP proxy password | `mypassword` |
| `https.proxyUser` | HTTPS proxy username | `myuser` |
| `https.proxyPassword` | HTTPS proxy password | `mypassword` |

#### SOCKS5 Proxy

SOCKS5 proxies are supported.

| Property                  | Description                        | Default | Example      |
|---------------------------|------------------------------------|---------|--------------|
| `socksProxyHost`          | SOCKS5 proxy hostname              | —       | `localhost`  |
| `socksProxyPort`          | SOCKS5 proxy port                  | `1080`  | `1080`       |
| `java.net.socks.username` | SOCKS5 username (if auth required) | —       | `myuser`     |
| `java.net.socks.password` | SOCKS5 password (if auth required) | —       | `mypassword` |

</details>

## Tools

### Analysis

- **analyze_code_snippet** - Analyze file content with SonarQube analyzers to identify code quality and security issues. Always analyzes the complete file content for accuracy. Optionally filter results to a specific code snippet.
  
  Usage:
  - **With workspace mounted** (recommended): pass `filePath` (project-relative) — the server reads the file directly, keeping file content out of the agent context window
  - **Without workspace mount**: pass complete `fileContent` for full file analysis (reports all issues)
  - Add optional `codeSnippet` to filter results - only issues within the snippet will be reported (snippet location auto-detected)
  
  Parameters:
  - `projectKey` - The SonarQube project key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `filePath` - Project-relative path of the file to analyze (e.g., `src/main/java/MyClass.java`). Used when the workspace is mounted at `/app/mcp-workspace` - _String_
  - `fileContent` - Complete file content as a string. Required when workspace is not mounted - _String_
  - `codeSnippet` - Code snippet to filter issues (must match content in fileContent) - _String_
  - `language` - Language of the code (e.g., 'java', 'python', 'javascript') - _String_
  - `scope` - Scope of the file: MAIN or TEST (default: MAIN) - _String_
  
  **Supported Languages:** Java, Kotlin, Python, Ruby, Go, JavaScript, TypeScript, JSP, PHP, XML, HTML, CSS, CloudFormation, Kubernetes, Terraform, Azure Resource Manager, Ansible, Docker, Secrets detection

**When integration with SonarQube for IDE is enabled:**
- **analyze_file_list** - Analyze files in the current working directory using SonarQube for IDE. This tool connects to a running SonarQube for IDE instance to perform code quality analysis on a list of files.
    - `file_absolute_paths` - List of absolute file paths to analyze - _Required String[]_


- **toggle_automatic_analysis** - Enable or disable SonarQube for IDE automatic analysis. When enabled, SonarQube for IDE will automatically analyze files as they are modified in the working directory. When disabled, automatic analysis is turned off.
    - `enabled` - Enable or disable the automatic analysis - _Required Boolean_

**When advanced analysis is enabled for your SonarQube Cloud organization:**

> Requires having the workspace mounted at `/app/mcp-workspace`

- **run_advanced_code_analysis** - Run advanced code analysis on SonarQube Cloud for a single file. Organization is inferred from MCP configuration.
    - `projectKey` - The key of the project - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
    - `branchName` - Branch name used to retrieve the latest analysis context - _Required String_
    - `filePath` - Project-relative path of the file to analyze (e.g., `src/main/java/MyClass.java`). - _Required String_
    - `fileScope` - Defines in which scope the file originates from: 'MAIN' or 'TEST' (default: MAIN) - _String_

### Coverage

- **search_files_by_coverage** - Search for files in a project sorted by coverage (ascending - worst coverage first). This tool helps identify files that need test coverage improvements.
  - `projectKey` - The project key to search in - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `pullRequest` - Pull request id to analyze - _String_
  - `maxCoverage` - Maximum coverage threshold (0-100). Only return files with coverage <= this value - _Number_
  - `pageIndex` - Page index (1-based, default: 1) - _Number_
  - `pageSize` - Page size (default: 100, max: 500) - _Number_


- **get_file_coverage_details** - Get line-by-line coverage information for a specific file, including which exact lines are uncovered and which have partially covered branches. This tool helps identify precisely where to add test coverage. Use after identifying files with low coverage via search_files_by_coverage.
  - `key` - File key (e.g. my_project:src/foo/Bar.java) - _Required String_
  - `pullRequest` - Pull request id - _String_
  - `from` - First line to analyze (1-based, default: 1) - _Number_
  - `to` - Last line to analyze (inclusive). If not specified, all lines are returned - _Number_

### Dependency Risks

**Note: Dependency risks are only available when connecting to SonarQube Server 2025.4 Enterprise or higher with SonarQube Advanced Security enabled.**

- **search_dependency_risks** - Search for software composition analysis issues (dependency risks) of a SonarQube project, paired with releases that appear in the analyzed project, application, or portfolio.
  - `projectKey` - Project key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `branchKey` - Optional branch key - _String_
  - `pullRequestKey` - Optional pull request key - _String_

### Enterprises

**Note: Enterprises are only available when connecting to SonarQube Cloud.**

- **list_enterprises** - List the enterprises available in SonarQube Cloud that you have access to. Use this tool to discover enterprise IDs that can be used with other tools.
    - `enterpriseKey` - Optional enterprise key to filter results - _String_

### Issues

- **change_sonar_issue_status** - Change the status of a SonarQube issue to "accept", "falsepositive" or to "reopen" an issue.
  - `key` - Issue key - _Required String_
  - `status` - New issue's status - _Required Enum {"accept", "falsepositive", "reopen"}_


- **search_sonar_issues_in_projects** - Search for SonarQube issues in my organization's projects.
  - `projects` - Optional list of Sonar projects - _String[]_
  - `pullRequestId` - Optional Pull Request's identifier - _String_
  - `severities` - Optional list of severities to filter by. Possible values: INFO, LOW, MEDIUM, HIGH, BLOCKER - _String[]_
  - `impactSoftwareQualities` - Optional list of software qualities to filter by. Possible values: MAINTAINABILITY, RELIABILITY, SECURITY - _String[]_
  - `issueStatuses` - Optional list of issue statuses to filter by. Possible values: OPEN, CONFIRMED, FALSE_POSITIVE, ACCEPTED, FIXED, IN_SANDBOX - _String[]_
  - `issueKey` - Optional issue key to fetch a specific issue - _String_
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Security Hotspots

- **search_security_hotspots** - Search for Security Hotspots in a SonarQube project.
  - `projectKey` - Project or application key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `hotspotKeys` - Comma-separated list of specific Security Hotspot keys to retrieve - _String[]_
  - `branch` - Optional branch key - _String_
  - `pullRequest` - Optional pull request key - _String_
  - `files` - Optional list of file paths to filter - _String[]_
  - `status` - Optional status filter: TO_REVIEW, REVIEWED - _String_
  - `resolution` - Optional resolution filter: FIXED, SAFE, ACKNOWLEDGED - _String_
  - `sinceLeakPeriod` - Filter hotspots created since the leak period (new code) - _Boolean_
  - `onlyMine` - Show only hotspots assigned to me - _Boolean_
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_


- **show_security_hotspot** - Get detailed information about a specific Security Hotspot, including rule details, code context, flows, and comments.
  - `hotspotKey` - Security Hotspot key - _Required String_


- **change_security_hotspot_status** - Review a Security Hotspot by changing its status. When marking as REVIEWED, you must specify a resolution (FIXED, SAFE, or ACKNOWLEDGED).
  - `hotspotKey` - Security Hotspot key - _Required String_
  - `status` - New status - _Required Enum {"TO_REVIEW", "REVIEWED"}_
  - `resolution` - Resolution when status is REVIEWED - _Enum {"FIXED", "SAFE", "ACKNOWLEDGED"}_
  - `comment` - Optional review comment - _String_

### Languages

- **list_languages** - List all programming languages supported in this SonarQube instance.
    - `q` - Optional pattern to match language keys/names against - _String_

### Measures

- **get_component_measures** - Get SonarQube measures for a component (project, directory, file).
  - `component` - Optional component key to get measures for - _String_
  - `metricKeys` - Optional metric keys to retrieve (e.g. ncloc, complexity, violations, coverage) - _String[]_
  - `pullRequest` - Optional pull request identifier to analyze for measures - _String_

### Metrics

- **search_metrics** - Search for SonarQube metrics.
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Portfolios

- **list_portfolios** - List enterprise portfolios available in SonarQube with filtering and pagination options.

  **For SonarQube Server:**
  - `q` - Optional search query to filter portfolios by name or key - _String_
  - `favorite` - If true, only returns favorite portfolios - _Boolean_
  - `pageIndex` - Optional 1-based page number (default: 1) - _Integer_
  - `pageSize` - Optional page size, max 500 (default: 100) - _Integer_

  **For SonarQube Cloud:**
  - `enterpriseId` - Enterprise uuid. Can be omitted only if 'favorite' parameter is supplied with value true - _String_
  - `q` - Optional search query to filter portfolios by name - _String_
  - `favorite` - Required to be true if 'enterpriseId' parameter is omitted. If true, only returns portfolios favorited by the logged-in user. Cannot be true when 'draft' is true - _Boolean_
  - `draft` - If true, only returns drafts created by the logged-in user. Cannot be true when 'favorite' is true - _Boolean_
  - `pageIndex` - Optional index of the page to fetch (default: 1) - _Integer_
  - `pageSize` - Optional size of the page to fetch (default: 50) - _Integer_

### Projects

- **search_my_sonarqube_projects** - Find SonarQube projects. The response is paginated.
  - `page` - Optional page number - _String_


- **list_pull_requests** - List all pull requests for a project. Use this tool to discover available pull requests before analyzing their coverage, issues, or quality. Returns the pull request key/ID which can be used with other tools (e.g., search_files_by_coverage, get_file_coverage_details).
  - `projectKey` - Project key (e.g. my_project) - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_

### Quality Gates

- **get_project_quality_gate_status** - Get the Quality Gate Status for the SonarQube project.
  - `analysisId` - Optional analysis ID - _String_
  - `projectId` - Optional project ID - _String_
  - `projectKey` - Optional project key - _String_
  - `pullRequest` - Optional pull request ID - _String_


- **list_quality_gates** - List all quality gates in my SonarQube.

### Rules

- **show_rule** - Shows detailed information about a SonarQube rule.
  - `key` - Rule key - _Required String_

### Duplications

- **search_duplicated_files** - Search for files with code duplications in a SonarQube project. By default, automatically fetches all duplicated files across all pages (up to 10,000 files max). Returns only files with duplications.
  - `projectKey` - Project key - _Required String_ _(Ignored when `SONARQUBE_PROJECT_KEY` is defined)_
  - `pullRequest` - Optional pull request id - _String_
  - `pageSize` - Optional number of results per page for manual pagination (max: 500). If not specified, auto-fetches all duplicated files - _Integer_
  - `pageIndex` - Optional page number for manual pagination (starts at 1). If not specified, auto-fetches all duplicated files - _Integer_


- **get_duplications** - Get duplications for a file. Require Browse permission on file's project.
  - `key` - File key - _Required String_
  - `pullRequest` - Optional pull request id - _String_

### Sources

- **get_raw_source** - Get source code as raw text from SonarQube. Require 'See Source Code' permission on file.
  - `key` - File key - _Required String_
  - `pullRequest` - Optional pull request id - _String_


- **get_scm_info** - Get SCM information of SonarQube source files. Require See Source Code permission on file's project.
  - `key` - File key - _Required String_
  - `commits_by_line` - Group lines by SCM commit if value is false, else display commits for each line - _String_
  - `from` - First line to return. Starts at 1 - _Number_
  - `to` - Last line to return (inclusive) - _Number_

### System

**Note: System tools are only available when connecting to SonarQube Server.**

- **get_system_health** - Get the health status of SonarQube Server instance. Returns GREEN (fully operational), YELLOW (usable but needs attention), or RED (not operational).


- **get_system_info** - Get detailed information about SonarQube Server system configuration including JVM state, database, search indexes, and settings. Requires 'Administer' permissions.


- **get_system_logs** - Get SonarQube Server system logs in plain-text format. Requires system administration permission.
  - `name` - Optional name of the logs to get. Possible values: access, app, ce, deprecation, es, web. Default: app - _String_


- **ping_system** - Ping the SonarQube Server system to check if it's alive. Returns 'pong' as plain text.


- **get_system_status** - Get state information about SonarQube Server. Returns status (STARTING, UP, DOWN, RESTARTING, DB_MIGRATION_NEEDED, DB_MIGRATION_RUNNING), version, and id.

### Webhooks

- **create_webhook** - Create a new webhook for the SonarQube organization or project. Requires 'Administer' permission on the specified project, or global 'Administer' permission.
  - `name` - Webhook name - _Required String_
  - `url` - Webhook URL - _Required String_
  - `projectKey` - Optional project key for project-specific webhook - _String_
  - `secret` - Optional webhook secret for securing the webhook payload - _String_


- **list_webhooks** - List all webhooks for the SonarQube organization or project. Requires 'Administer' permission on the specified project, or global 'Administer' permission.
  - `projectKey` - Optional project key to list project-specific webhooks - _String_

### Context Augmentation

<details>
<summary>Architecture Tools</summary>

- **search_by_signature_patterns** - Find code elements (classes, methods, interfaces, ...) by their declaration signatures using regex patterns.
    - `include_code_regex_list` - List of regex patterns to match against signatures - _Required String[]_
    - `exclude_code_regex_list` - List of regex patterns to exclude from results - _String[]_
    - `include_glob` - File filter glob pattern (e.g., `*.java`) - _String_
    - `exclude_glob` - File exclusion glob pattern - _String_
    - `fields` - Comma-separated list of fields to include in the response - _String_
    - `limit` - Maximum number of results to return (default: 10) - _Integer_
    - `regex_lists_operator` - How to combine multiple patterns: `OR` (default) or `AND` - _String_


- **search_by_body_patterns** - Find code elements by their implementation body using regex patterns. Useful for locating where APIs or patterns are actually used.
    - `include_code_regex_list` - List of regex patterns to match in code bodies - _Required String[]_
    - `exclude_code_regex_list` - List of regex patterns to exclude from results - _String[]_
    - `include_glob` - File filter glob pattern - _String_
    - `exclude_glob` - File exclusion glob pattern - _String_
    - `fields` - Comma-separated list of fields to include in the response - _String_
    - `limit` - Maximum number of results to return (default: 10) - _Integer_
    - `regex_lists_operator` - How to combine multiple patterns: `OR` (default) or `AND` - _String_


- **get_upstream_call_flow** - Trace what functions call a given function. Useful for finding all callers and entry points, and understanding what breaks if a signature changes.
    - `fqn` - Fully qualified name of the function - _Required String_
    - `depth` - Call chain depth (0=function only, 1=direct callers, etc.) - _Integer_
    - `fields` - Comma-separated list of fields to include in the response - _String_


- **get_downstream_call_flow** - Trace what functions a given function calls. Useful for impact analysis and understanding execution flow.
    - `fqn` - Fully qualified name of the function - _Required String_
    - `depth` - Call chain depth (0=function only, 1=direct callees, etc.) - _Integer_
    - `fields` - Comma-separated list of fields to include in the response - _String_


- **get_source_code** - Get complete source code (signature and body) for a code element by its fully qualified name.
    - `fqn` - Fully qualified name of the element - _Required String_
    - `fields` - Comma-separated list of fields to include in the response - _String_


- **get_type_hierarchy** - Get the full inheritance hierarchy for a class-like structure (class, interface, enum, record, exception, struct). Essential for understanding inheritance trees and refactoring.
    - `fqn` - Fully qualified name of the class-like structure - _Required String_
    - `fields` - Comma-separated list of fields to include in the response - _String_


- **get_references** - Get direct inbound and outbound code references for a class or module. Returns only direct (non-transitive) references.
    - `fqn` - Fully qualified name of the class or module - _Required String_
    - `fields` - Comma-separated list of fields to include in the response - _String_


- **get_current_architecture** - Get a hierarchical architecture graph filtered by path prefix and depth. Useful for exploring module structure and high-level dependencies.
    - `depth` - Hierarchy depth (0=root only, 1=root + children, etc.) - _Required Integer_
    - `path_prefix` - Optional path prefix to filter nodes (e.g., `com.example.service`) - _String_
    - `ecosystem` - Optional ecosystem to filter by (`java`, `cs`, `py`, `js`, `ts`) - _String_


- **get_intended_architecture** - Get user-defined architectural constraints specifying which modules are allowed to depend on others.

</details>

<details>
<summary>Guidelines Tools</summary>

- **get_guidelines** - Get coding guidelines based on SonarQube project issues, catalog categories, or a combination of both.
    - `mode` - Guidelines retrieval mode: `project_based`, `category_based`, or `combined` - _Required String_
    - `categories` - List of category names (required for `category_based` and `combined` modes) - _String[]_
    - `languages` - List of target languages in SonarQube repository key format (required when `categories` is provided) - _String[]_
    - `file_paths` - Optional list of file paths to filter guidelines by - _String[]_

</details>

<details>
<summary>Third-party Dependency Tools</summary>

- **check_dependency** - Check a third-party dependency for security vulnerabilities, supply-chain malware, and license compliance before adding or updating it.
    - `purl` - Package URL (purl) with version, per [purl-spec](https://github.com/package-url/purl-spec). Format: `pkg:<type>/<namespace>/<name>@<version>` (e.g. `pkg:npm/lodash@4.17.21`, `pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1`, `pkg:pypi/django@3.2.0`) - _Required String_

</details>

<details>
<summary>Context Augmentation Environment Variables</summary>

| Variable                 | Description                                                        | Required | Default                 |
|--------------------------|--------------------------------------------------------------------|----------|-------------------------|
| `SONARQUBE_URL`          | SonarQube Cloud URL                                                | Yes      | `https://sonarcloud.io` |
| `SONARQUBE_TOKEN`        | Authentication token                                               | Yes      | None                    |
| `SONARQUBE_ORG`          | Organization key on SonarQube Cloud                                | Yes      | None                    |
| `SONARQUBE_PROJECT_KEY`  | Project key on SonarQube Cloud                                     | Yes      | None                    |
| `SONAR_SQ_BRANCH`        | Explicit SonarQube branch override *                               | No       | None                    |
| `SONARQUBE_DEBUG_ENABLED`| Activate debug logging (for troubleshooting)                       | No       | False                   |
| `SONAR_LOG_LEVEL`        | Logging verbosity (`TRACE`, `DEBUG`, `INFO`, `WARNING`, `ERROR`)   | No       | `INFO`                  |

* To be provided when not using git, or when the git branch name doesn't match the branch name in SonarQube.

</details>

<details>
<summary>Project-Specific Configuration (Recommended)</summary>

First, export the `SONARQUBE_TOKEN` [environment variable](https://docs.sonarsource.com/sonarqube-mcp-server/build-and-configure/environment-variables#common-variables) with a valid [Personal Access Token (PAT)](https://docs.sonarsource.com/sonarqube-cloud/managing-your-account/managing-tokens) for your project.

```bash
# macOS/Linux (Bash/Zsh)
export SONARQUBE_TOKEN="{<YourUserToken>}"
```

Then, mount the project workspace to give the Context Augmentation server direct access to your source files:

```json
{
  "mcpServers": {
    "sonarqube-mcp-server": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm", "--pull=always",
        "-e", "SONARQUBE_URL",
        "-e", "SONARQUBE_TOKEN",
        "-e", "SONARQUBE_ORG",
        "-e", "SONARQUBE_PROJECT_KEY",
        "-e", "SONARQUBE_TOOLSETS",
        "-v", "/ABSOLUTE/PATH/TO/YOUR/PROJECT:/app/mcp-workspace:rw",
        "mcp/sonarqube"
      ],
      "env": {
        "SONARQUBE_URL": "https://sonarcloud.io",
        "SONARQUBE_ORG": "<YourOrganizationKey>",
        "SONARQUBE_PROJECT_KEY": "<YourProjectKey>",
        "SONARQUBE_TOOLSETS": "cag"
      }
    }
  }
}
```

**Important**: In a project-scoped config, do not put `SONARQUBE_TOKEN` in the env block. Export it as an environment variable (`export SONARQUBE_TOKEN=...`). Docker will forward it into the container via `-e SONARQUBE_TOKEN`.

</details>

## Example Prompts

Once you've set up the SonarQube MCP Server, here are some example prompts for common real-world scenarios:

<details>
<summary>Fixing a Failing Quality Gate</summary>

```
My quality gate is failing for my project. Can you help me understand why and fix the most critical issues?
```

```
The quality gate on my feature branch is red. What do I need to fix to get it passing before I can merge to main?
```

</details>

<details>
<summary>Pre-Release and Pre-Merge Checks</summary>

```
I'm about to merge my pull request <#247> for the <web-app> project. Can you check if there are any quality issues I should address first?
```

```
We're deploying to production tomorrow. Can you check the quality gate status and alert me to any critical issues in this branch?
```

</details>

<details>
<summary>Improving Code Quality</summary>

```
I want to reduce technical debt in my project. What are the top issues I should prioritize?
```

```
Our code coverage dropped below 70%. Can you identify which files have the lowest coverage and help me improve it?
```

</details>

<details>
<summary>Understanding and Fixing Issues</summary>

```
I have 15 new code smells in my latest commit. Can you explain what they are and help me fix them?
```

```
SonarQube flagged a critical security vulnerability in <AuthController.java>. What's the issue and how do I fix it?
```

</details>

<details>
<summary>Security and Dependency Management</summary>

```
We need to pass a security audit. Can you check all our projects for security vulnerabilities and create a prioritized list of what needs to be fixed?
```

```
Are there any known vulnerabilities in our dependencies? Check this project for dependency risks.
```

</details>

<details>
<summary>Code Review Assistance</summary>

```
I just wrote this authentication function. Can you analyze it for security issues and code quality problems before I commit?
```

```
Review the changes in <src/database/migrations> for any potential bugs or security issues.
```

</details>

<details>
<summary>Project Health Monitoring</summary>

```
Give me a health report for my project: quality gate status, number of bugs, Security Hotspots, and code coverage.
```

```
Compare code quality between our main branch and the develop branch. Are we introducing new issues?
```

</details>

<details>
<summary>Team Collaboration</summary>

```
What are the most common rule violations across all our projects? We might need to update our coding standards.
```

```
Show me all the issues that were marked as false positives in the last month. Are we seeing patterns that suggest our rules need adjustment?
```

</details>

## Build

Prefer the container image from [mcp/sonarqube](https://hub.docker.com/r/mcp/sonarqube) — build from source only if you need to run a locally modified version of the server.

<details>
<summary>Build from source</summary>

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

</details>

## Troubleshooting

Application logs are written to the `STORAGE_PATH/logs/mcp.log` file by default. To disable file logging entirely, set `SONARQUBE_LOG_TO_FILE_DISABLED=true`.

### Common Issues

#### "Feature is not working" or "Missing tools/functionality"

You may be running an outdated Docker image. Docker caches images locally, so you won't automatically receive updates.

**Solution:** Update to the latest version:

```bash
docker pull mcp/sonarqube:latest
```

After pulling the latest image, restart your MCP client to use the updated version.

Optionally, add the `--pull=always` flag to your docker run command to always check for and pull the latest version:

```bash
docker run --init --pull=always -i --rm -e SONARQUBE_TOKEN -e SONARQUBE_ORG mcp/sonarqube
```

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/). Using the SonarQube MCP Server in compliance with this documentation is a Non-Competitive Purpose and so is allowed under the SSAL.

Your use of SonarQube via MCP is governed by the [SonarQube Cloud Terms of Service](https://www.sonarsource.com/legal/sonarcloud/terms-of-service/) or [SonarQube Server Terms and Conditions](https://www.sonarsource.com/legal/sonarqube/terms-and-conditions/), including use of the Results Data solely for your internal software development purposes.
