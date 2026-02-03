# SonarQube MCP Server

[![Build](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/SonarSource/sonarqube-mcp-server/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SonarSource_sonarqube-mcp-server&metric=alert_status&token=364a508a1e77096460f8571d8e66b41c99c95bea)](https://sonarcloud.io/summary/new_code?id=SonarSource_sonarqube-mcp-server)

The SonarQube MCP Server is a Model Context Protocol (MCP) server that enables seamless integration with SonarQube Server or Cloud for code quality and security.
It also supports the analysis of code snippet directly within the agent context.

## Quick setup

The simplest method is to rely on our container image hosted at [mcp/sonarqube](https://hub.docker.com/r/mcp/sonarqube). Read below if you want to build it locally.

> **Note:** While the examples below use `docker`, any OCI-compatible container runtime works (e.g., Podman, nerdctl). Simply replace `docker` with your preferred tool.

<details>
<summary>Claude Code</summary>

* To connect with SonarQube Cloud:

`claude mcp add sonarqube --env SONARQUBE_TOKEN=<token> --env SONARQUBE_ORG=<org> -- docker run -i --rm -e SONARQUBE_TOKEN -e SONARQUBE_ORG mcp/sonarqube`

* To connect with SonarQube Server:

`claude mcp add sonarqube --env SONARQUBE_TOKEN=<token> --env SONARQUBE_URL=<url> -- docker run -i --rm -e SONARQUBE_TOKEN -e SONARQUBE_URL mcp/sonarqube`

</details>

<details>
<summary>Codex CLI</summary>

In `~/.codex/config.toml`, add the following configuration:

* To connect with SonarQube Cloud:

```
[mcp_servers.sonarqube]
command = "docker"
args = ["run", "--rm", "-i", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_ORG", "mcp/sonarqube"]
env = { "SONARQUBE_TOKEN" = "<YOUR_USER_TOKEN>", "SONARQUBE_ORG" = "<YOUR_ORG>" }
```

* To connect with SonarQube Server:

```
[mcp_servers.sonarqube]
command = "docker"
args = ["run", "--rm", "-i", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_URL", "mcp/sonarqube"]
env = { "SONARQUBE_TOKEN" = "<YOUR_TOKEN>", "SONARQUBE_URL" = "<YOUR_SERVER_URL>" }
```

</details>

<details>
<summary>Cursor</summary>

* To connect with SonarQube Cloud:

[![Install for SonarQube Cloud](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/en-US/install-mcp?name=sonarqube&config=eyJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiWU9VUl9UT0tFTiIsIlNPTkFSUVVCRV9PUkciOiJZT1VSX1NPTkFSUVVCRV9PUkcifSwiY29tbWFuZCI6ImRvY2tlciBydW4gLWkgLS1ybSAtZSBTT05BUlFVQkVfVE9LRU4gLWUgU09OQVJRVUJFX09SRyBtY3Avc29uYXJxdWJlIn0%3D)

* To connect with SonarQube Server:

[![Install for SonarQube Server](https://cursor.com/deeplink/mcp-install-dark.svg)](https://cursor.com/en-US/install-mcp?name=sonarqube&config=eyJlbnYiOnsiU09OQVJRVUJFX1RPS0VOIjoiWU9VUl9VU0VSX1RPS0VOIiwiU09OQVJRVUJFX1VSTCI6IllPVVJfU09OQVJRVUJFX1VSTCJ9LCJjb21tYW5kIjoiZG9ja2VyIHJ1biAtaSAtLXJtIC1lIFNPTkFSUVVCRV9UT0tFTiAtZSBTT05BUlFVQkVfVVJMIG1jcC9zb25hcnF1YmUifQ%3D%3D)

</details>

<details>
<summary>Gemini CLI</summary>

You can install our MCP server extension by using the following command:

`gemini extensions install https://github.com/SonarSource/sonarqube-mcp-server`

You will need to set the required environment variables before starting Gemini:

```
SONARQUBE_TOKEN="<token>"
SONARQUBE_ORG="<org>" // For SonarQube Cloud, empty otherwise
SONARQUBE_URL="<url>" // For SonarQube Server, empty otherwise
```

Once installed, the extension will be installed under `<home>/.gemini/extensions/sonarqube-mcp-server/gemini-extension.json`.

</details>

<details>
<summary>GitHub Copilot CLI</summary>

After starting Copilot CLI, run the following command to add the SonarQube MCP server:

`/mcp add`

You will have to provide different information about the MCP server, you can use tab to navigate between fields.

* To connect with SonarQube Cloud:

```
Server Name: sonarqube
Server Type: Local (Press 1)
Command: docker
Arguments: run, --rm, -i, -e, SONARQUBE_TOKEN, -e, SONARQUBE_ORG, mcp/sonarqube
Environment Variables: SONARQUBE_TOKEN=<YOUR_TOKEN>,SONARQUBE_ORG=<YOUR_ORG>
Tools: *
```

* To connect with SonarQube Server:

```
Server Name: sonarqube
Server Type: Local (Press 1)
Command: docker
Arguments: run, --rm, -i, -e, SONARQUBE_TOKEN, -e, SONARQUBE_ORG, mcp/sonarqube
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

* To connect with SonarQube Server:

```
{
  "mcpServers": {
    "sonarqube": {
      "type": "local",
      "command": "docker",
      "args": [
        "run",
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

Create a `.kiro/settings/mcp.json` file in your workspace directory (or edit if it already exists), add the following configuration:

* To connect with SonarQube Cloud:

```
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
        "SONARQUBE_TOKEN": "<YOUR_TOKEN>",
        "SONARQUBE_ORG": "<YOUR_ORG>"
      },
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

* To connect with SonarQube Server:

```
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

[![Install for SonarQube Cloud](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Cloud-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_ORG%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Cloud%20Organization%20Key%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_ORG%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_ORG%22%3A%22%24%7Binput%3ASONARQUBE_ORG%7D%22%7D%7D)

[![Install for SonarQube Server](https://img.shields.io/badge/VS_Code-Install_for_SonarQube_Server-0098FF?style=flat-square&logo=visualstudiocode&logoColor=white)](https://insiders.vscode.dev/redirect/mcp/install?name=sonarqube&inputs=%5B%7B%22id%22%3A%22SONARQUBE_TOKEN%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20User%20Token%22%2C%22password%22%3Atrue%7D%2C%7B%22id%22%3A%22SONARQUBE_URL%22%2C%22type%22%3A%22promptString%22%2C%22description%22%3A%22SonarQube%20Server%20URL%22%2C%22password%22%3Afalse%7D%5D&config=%7B%22command%22%3A%22docker%22%2C%22args%22%3A%5B%22run%22%2C%22-i%22%2C%22--rm%22%2C%22-e%22%2C%22SONARQUBE_TOKEN%22%2C%22-e%22%2C%22SONARQUBE_URL%22%2C%22mcp%2Fsonarqube%22%5D%2C%22env%22%3A%7B%22SONARQUBE_TOKEN%22%3A%22%24%7Binput%3ASONARQUBE_TOKEN%7D%22%2C%22SONARQUBE_URL%22%3A%22%24%7Binput%3ASONARQUBE_URL%7D%22%7D%7D)

</details>

<details>
<summary>Windsurf</summary>

SonarQube MCP Server is available as a Windsurf plugin. Follow these instructions:

1. Click on the `Plugins` button at the top right of the Cascade view
2. Search for `sonarqube` on the Plugin store
3. Click `Install`
4. Add the required SonarQube token. Then add the organization key if you want to connect with SonarQube Cloud, or the SonarQube URL if you want to connect to SonarQube Server or Community Build.

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

## Integration with SonarQube for IDE

The SonarQube MCP Server can integrate with [SonarQube for IDE](https://www.sonarsource.com/products/sonarlint/) to further enhance your development workflow, providing better code analysis and insights directly within your IDE.

When using SonarQube for IDE, the `SONARQUBE_IDE_PORT` environment variable should be set with the correct port number. SonarQube for VS Code includes a Quick Install button, which automatically sets the correct port configuration.

For example, with SonarQube Cloud:

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

### Base

You should add the following variable when running the MCP Server:

| Environment variable | Description                                                                                                                                                                                                 |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `STORAGE_PATH`       | Mandatory absolute path to a writable directory where SonarQube MCP Server will store its files (e.g., for creation, updates, and persistence), it is automatically provided when using the container image |
| `SONARQUBE_IDE_PORT` | Optional port number between 64120 and 64130 used to connect SonarQube MCP Server with SonarQube for IDE.                                                                                                   |

### Selective Tool Enablement

By default, all tools are enabled. You can selectively enable specific toolsets to reduce context overhead and focus on specific functionality.

| Environment variable   | Description                                                                                                                                                                                                                                    |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SONARQUBE_TOOLSETS`   | Comma-separated list of toolsets to enable. When set, only these toolsets will be available. If not set, all tools are enabled. **Note:** The `projects` toolset is always enabled as it's required to find project keys for other operations. |
| `SONARQUBE_READ_ONLY`  | When set to `true`, enables read-only mode which disables all write operations (changing issue status for example). This filter is cumulative with `SONARQUBE_TOOLSETS` if both are set. Default: `false`.                                     |

<details>
<summary>Available Toolsets</summary>

| Toolset            | Key                 | Description                                                          |
|--------------------|---------------------|----------------------------------------------------------------------|
| **Analysis**       | `analysis`          | Code analysis tools (analyze code snippets and files)                |
| **Issues**         | `issues`            | Search and manage SonarQube issues                                   |
| **Projects**       | `projects`          | Browse and search SonarQube projects                                 |
| **Quality Gates**  | `quality-gates`     | Access quality gates and their status                                |
| **Rules**          | `rules`             | Browse and search SonarQube rules                                    |
| **Sources**        | `sources`           | Access source code and SCM information                               |
| **Measures**       | `measures`          | Retrieve metrics and measures (includes both measures and metrics tools) |
| **Languages**      | `languages`         | List supported programming languages                                 |
| **Portfolios**     | `portfolios`        | Manage portfolios and enterprises (Cloud and Server)                 |
| **System**         | `system`            | System administration tools (Server only)                            |
| **Webhooks**       | `webhooks`          | Manage webhooks                                                      |
| **Dependency Risks**| `dependency-risks` | Analyze dependency risks and security issues (SCA)                   |

#### Examples

**Enable analysis, issues, and quality gates toolsets (using Docker with SonarQube Cloud):**

```bash
docker run -i --rm \
  -e SONARQUBE_TOKEN="<token>" \
  -e SONARQUBE_ORG="<org>" \
  -e SONARQUBE_TOOLSETS="analysis,issues,quality-gates" \
  mcp/sonarqube
```

Note: The `projects` toolset is always enabled automatically, so you don't need to include it in `SONARQUBE_TOOLSETS`.

**Enable read-only mode (using Docker with SonarQube Cloud):**

```bash
docker run -i --rm \
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

> 💡 **Configuration Tip**: The presence of `SONARQUBE_ORG` determines whether you're connecting to SonarQube Cloud or Server. If `SONARQUBE_ORG` is set, SonarQube Cloud is used; otherwise, SonarQube Server is used.

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
      "args": ["run", "-i", "--rm", "-e", "SONARQUBE_TOKEN", "-e", "SONARQUBE_ORG", "mcp/sonarqube"],
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

**Note:** In HTTP(S) mode, each client sends their own token via the `SONARQUBE_TOKEN` header. The server's `SONARQUBE_TOKEN` is only used for initialization.

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
docker run -p 8443:8443 \
  -v $(pwd)/keystore.p12:/etc/ssl/mcp/keystore.p12:ro \
  -e SONARQUBE_TRANSPORT=https \
  -e SONARQUBE_HTTP_PORT=8443 \
  -e SONARQUBE_TOKEN="<init-token>" \
  -e SONARQUBE_ORG="<your-org>" \
  mcp/sonarqube
```

**Client Configuration:**
```json
{
  "mcpServers": {
    "sonarqube-https": {
      "url": "https://your-server:8443/mcp",
      "headers": {
        "SONARQUBE_TOKEN": "<your-token>"
      }
    }
  }
}
```

**Note:** For local development, use Stdio transport instead (the default). HTTPS is intended for multi-user production deployments with proper SSL certificates.

### Custom Certificates

If your SonarQube Server uses a self-signed certificate or a certificate from a private Certificate Authority (CA), you can add custom certificates to the container that will automatically be installed.

<details>
<summary>Configuration</summary>

#### Using Volume Mount

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

</details>

### Proxy

The SonarQube MCP Server supports HTTP proxies through standard Java proxy system properties.

<details>
<summary>Configuration</summary>

#### Configuring Proxy Settings

You can configure proxy settings using Java system properties. These can be set as environment variables or passed as JVM arguments.

**Common Proxy Properties:**

| Property | Description | Example |
|----------|-------------|---------|
| `http.proxyHost` | HTTP proxy hostname | `proxy.example.com` |
| `http.proxyPort` | HTTP proxy port | `8080` |
| `https.proxyHost` | HTTPS proxy hostname | `proxy.example.com` |
| `https.proxyPort` | HTTPS proxy port | `8443` |
| `http.nonProxyHosts` | Hosts that bypass the proxy (pipe-separated) | `localhost\|127.0.0.1\|*.internal.com` |

#### Proxy Authentication

If your proxy requires authentication, the SonarQube MCP Server uses Java's standard authentication mechanism. You can set up proxy credentials using Java system properties:

| Property | Description | Example |
|----------|-------------|---------|
| `http.proxyUser` | HTTP proxy username | `myuser` |
| `http.proxyPassword` | HTTP proxy password | `mypassword` |
| `https.proxyUser` | HTTPS proxy username | `myuser` |
| `https.proxyPassword` | HTTPS proxy password | `mypassword` |

</details>

## Tools

### Analysis

- **analyze_code_snippet** - Analyze a file or code snippet with SonarQube analyzers to identify code quality and security issues. Specify the language of the snippet to improve analysis accuracy.
  - `codeSnippet` - Code snippet or full file content - _Required String_
  - `language` - Optional language of the code snippet - _String_

**When integration with SonarQube for IDE is enabled:**
- **analyze_file_list** - Analyze files in the current working directory using SonarQube for IDE. This tool connects to a running SonarQube for IDE instance to perform code quality analysis on a list of files.
    - `file_absolute_paths` - List of absolute file paths to analyze - _Required String[]_


- **toggle_automatic_analysis** - Enable or disable SonarQube for IDE automatic analysis. When enabled, SonarQube for IDE will automatically analyze files as they are modified in the working directory. When disabled, automatic analysis is turned off.
    - `enabled` - Enable or disable the automatic analysis - _Required Boolean_

### Dependency Risks

**Note: Dependency risks are only available when connecting to SonarQube Server 2025.4 Enterprise or higher with SonarQube Advanced Security enabled.**

- **search_dependency_risks** - Search for software composition analysis issues (dependency risks) of a SonarQube project, paired with releases that appear in the analyzed project, application, or portfolio.
  - `projectKey` - Project key - _String_
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
  - `branch` - Optional branch name to search for issues in - _String_
  - `pullRequestId` - Optional Pull Request's identifier - _String_
  - `severities` - Optional list of severities to filter by. Possible values: INFO, LOW, MEDIUM, HIGH, BLOCKER - _String[]_
  - `impactSoftwareQualities` - Optional list of software qualities to filter by. Possible values: MAINTAINABILITY, RELIABILITY, SECURITY - _String[]_
  - `issueStatuses` - Optional list of issue statuses to filter by. Possible values: OPEN, CONFIRMED, FALSE_POSITIVE, ACCEPTED, FIXED, IN_SANDBOX - _String[]_
  - `issueKey` - Optional issue key to fetch a specific issue - _String_
  - `p` - Optional page number (default: 1) - _Integer_
  - `ps` - Optional page size. Must be greater than 0 and less than or equal to 500 (default: 100) - _Integer_

### Languages

- **list_languages** - List all programming languages supported in this SonarQube instance.
    - `q` - Optional pattern to match language keys/names against - _String_

### Measures

- **get_component_measures** - Get SonarQube measures for a component (project, directory, file).
  - `component` - Optional component key to get measures for - _String_
  - `branch` - Optional branch to analyze for measures - _String_
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

### Quality Gates

- **get_project_quality_gate_status** - Get the Quality Gate Status for the SonarQube project.
  - `analysisId` - Optional analysis ID - _String_
  - `branch` - Optional branch key - _String_
  - `projectId` - Optional project ID - _String_
  - `projectKey` - Optional project key - _String_
  - `pullRequest` - Optional pull request ID - _String_


- **list_quality_gates** - List all quality gates in my SonarQube.

### Rules

- **list_rule_repositories** - List rule repositories available in SonarQube.
  - `language` - Optional language key - _String_
  - `q` - Optional search query - _String_


- **show_rule** - Shows detailed information about a SonarQube rule.
  - `key` - Rule key - _Required String_

### Sources

- **get_raw_source** - Get source code as raw text from SonarQube. Require 'See Source Code' permission on file.
  - `key` - File key - _Required String_
  - `branch` - Optional branch key - _String_
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

## Troubleshooting

Applications logs will be written to the `STORAGE_PATH/logs/mcp.log` file.

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/). Using the SonarQube MCP Server in compliance with this documentation is a Non-Competitive Purpose and so is allowed under the SSAL.

Your use of SonarQube via MCP is governed by the [SonarQube Cloud Terms of Service](https://www.sonarsource.com/legal/sonarcloud/terms-of-service/) or [SonarQube Server Terms and Conditions](https://www.sonarsource.com/legal/sonarqube/terms-and-conditions/), including use of the Results Data solely for your internal software development purposes.
