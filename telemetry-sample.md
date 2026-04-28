
```json
{
  "days_since_installation": 27,
  "days_of_use": 5,
  "sonarlint_version": "0.0.1",
  "sonarlint_product": "mcpserver",
  "ide_version": "1.53.0",
  "connected_mode_used": true,
  "connected_mode_sonarcloud": true,
  "system_time": "2018-02-27T16:31:49.173+01:00",
  "install_time": "2018-02-01T16:30:49.124+01:00",
  "analyses": [
    {
      "language": "js",
      "rate_per_duration": { "0-300": 100, "300-500": 0, "500-1000": 0, "1000-2000": 0, "2000-4000": 0, "4000+": 0 }
    }
  ],
  "os": "Linux",
  "platform": "linux",
  "architecture": "x64",
  "jre": "17.0.5",
  "nodejs": "11.12.0",
  "metric_values": [
    {
      "key": "tools.search_sonar_issues_success_count",
      "value": "1",
      "type": "INTEGER",
      "granularity": "DAILY"
    },
    {
      "key": "tools.search_sonar_issues_error_count",
      "value": "1",
      "type": "INTEGER",
      "granularity": "DAILY"
    },
    {
      "key": "mcp.integration_enabled",
      "value": "true",
      "type": "BOOLEAN",
      "granularity": "DAILY"
    },
    {
      "key": "mcp.transport_mode",
      "value": "STDIO",
      "type": "STRING",
      "granularity": "DAILY"
    }
  ]
}
```
On tool invocation:
```json
{
  "invocation_id": "123e4567-e89b-12d3-a456-426655440000",
  "tool_name": "search_sonar_issues",
  "connection_type": "SQC",
  "organization_uuid_v4": "123e4567-e89b-12d3-a456-426655440000",
  "sqs_installation_id": null,
  "user_uuid": "123e4567-e89b-12d3-a456-426655440000",
  "mcp_server_id": "123e4567-e89b-12d3-a456-426655440000",
  "mcp_server_version": "1.11.0.12345",
  "transport_mode": "stdio",
  "calling_agent_name": "cursor-vscode",
  "calling_agent_version": "1.0.0",
  "tool_execution_duration_ms": 342,
  "is_successful": true,
  "error_type": null,
  "response_size_bytes": 8472,
  "container_arch": "arm64",
  "invocation_timestamp": 1741036799658
}
```
