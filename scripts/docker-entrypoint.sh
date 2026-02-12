#!/bin/sh
set -e

# Install certificates if any exist
/usr/local/bin/install-certificates

# Replace the shell with the Java process using exec.
# This is critical for proper signal handling in Docker:
# - Makes Java PID 1, so it directly receives SIGTERM from Docker
# - Ensures the shutdown hook in SonarQubeMcpServer.java runs on container stop
# - Without exec, the shell stays as PID 1 and may not forward signals properly
exec java -jar /app/sonarqube-mcp-server.jar
