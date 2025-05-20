# sonar-mcp-server

## Building

The task `preparePlugins` will generate directories for plugins/omnisharp in `build/sonar-mcp-server`.

`build` will generate the Jar file in `build/libs`.

```bash
./gradlew clean build preparePlugins
```

## Running the Application

Once the JAR is built, you can run it using the following command:

```bash
java                     
-DSTORAGE_PATH=PATH_TO_REPLACE
-DPLUGIN_PATH=PATH_TO_REPLACE
-DSONARQUBE_CLOUD_TOKEN=TOKEN
-DSONARQUBE_CLOUD_ORG=ORG
-jar build/libs/sonar-mcp-server-<version>.jar
```

Replace `<version>` with the actual version of the JAR file.

## Running as an MCP Server

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

## Data and telemetry

This server collects anonymous usage data and sends it to SonarSource to help improve the product. No source code or IP address is collected, and SonarSource does not share the data with anyone else. Collection of telemetry can be disabled with the following system property or environment variable: `TELEMETRY_DISABLED=true`. Click [here](telemetry-sample.md) to see a sample of the data that are collected.

## License

Copyright 2025 SonarSource.

Licensed under the [SONAR Source-Available License v1.0](https://www.sonarsource.com/license/ssal/)
