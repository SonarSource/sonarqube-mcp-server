# sonar-mcp-server

## Building

Run the following Gradle command to clean the project, prepare the necessary plugins, and build the application:

```bash
./gradlew clean build preparePlugins
```

The `preparePlugins` task generates plugin directories (e.g., analyzers/omnisharp) under `build/sonar-mcp-server/`

The JAR file will be created in `build/libs/`

## Running the MCP Server

Once built, you can run the application using:

```bash
java \
-DSTORAGE_PATH=PATH_TO_REPLACE \
-DPLUGIN_PATH=PATH_TO_REPLACE \
-DSONARQUBE_CLOUD_TOKEN=TOKEN \
-DSONARQUBE_CLOUD_ORG=ORG \
-jar build/libs/sonar-mcp-server-<version>.jar
```

Replace `<version>` with the actual version of the JAR file.

## Integration into an MCP Client

Use the following JSON configuration when integrating with an MCP Client:

```JSON
        {
            "sonar-mcp-server": {
                "command": "java",
                "args": [
                    "-jar",
                    "build/libs/sonar-mcp-server-0.0.1-SNAPSHOT.jar"
                ],
                "env": {
                  "STORAGE_PATH": "<path_to_your_mcp_storage>",
                  "PLUGIN_PATH": "<path_to_the_sonar_plugins>",
                  "SONARQUBE_CLOUD_TOKEN": "<sonarqube_cloud_user_token>",
                  "SONARQUBE_CLOUD_ORG": "<sonarqube_cloud_organization>"
                }
            }
        }
```

## Running the MCP Server with Docker

First, build the Docker image:

```bash
./gradlew buildDocker
```

Then, run the image as follows:

```JSON
        {
  "sonar-mcp-server-docker": {
    "command": "docker",
    "args": [
      "run",
      "-i",
      "-v",
      "<path_to_folder_plugins>:/app/plugins",
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

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/)
