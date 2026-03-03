/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gessie-compatible envelope wrapping every analytics event sent to the /mcp endpoint.
 */
public record AnalyticsEnvelope(
  @JsonProperty("metadata") Metadata metadata,
  @JsonProperty("event_payload") Object eventPayload
) {

  public record Metadata(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("source") Source source,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_timestamp") String eventTimestamp,
    @JsonProperty("event_version") String eventVersion
  ) {
  }

  public record Source(
    @JsonProperty("domain") String domain
  ) {
  }

}
