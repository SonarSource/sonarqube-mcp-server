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
