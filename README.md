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
-DWORKDIR_PATH=PATH_TO_REPLACE
-DMCP_HOME_PATH=PATH_TO_REPLACE
-DPLUGIN_PATH=PATH_TO_REPLACE
-jar build/libs/sonar-mcp-server-<version>.jar
```

Replace `<version>` with the actual version of the JAR file.
