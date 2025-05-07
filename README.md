# sonar-mcp-server

Sonar MCP Server

## Running the Application

Once the JAR is built, you can run it using the following command:

```bash
java -jar build/libs/sonar-mcp-server-<version>.jar
```

Replace `<version>` with the actual version of the JAR file.

### Notes

- The application requires Java 21 or later to run.
- The JAR includes all necessary dependencies, including the sloop files needed for the backend service.
- The application will use the configuration from `application.properties` by default. You can override these settings by providing command-line arguments or environment variables.

Example with custom configuration:

```bash
java -jar build/libs/sonar-mcp-server-<version>.jar --app.sloop.path=/custom/path/to/sloop
```
