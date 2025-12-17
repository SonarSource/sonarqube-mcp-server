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
package org.sonarsource.sonarqube.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectMapperConfigurationTest {

  @Test
  void objectMapper_should_ignore_unknown_properties() {
    var objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    
    var mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);

    // Simulate a newer MCP protocol message with unknown "form" field in elicitation
    var initializeRequestWithUnknownField = """
      {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2024-11-05",
          "capabilities": {
            "elicitation": {
              "form": true
            }
          },
          "clientInfo": {
            "name": "test-client",
            "version": "1.0.0"
          }
        }
      }
      """;

    // This should not throw an exception with FAIL_ON_UNKNOWN_PROPERTIES disabled
    assertDoesNotThrow(() -> {
      var message = McpSchema.deserializeJsonRpcMessage(mcpJsonMapper, initializeRequestWithUnknownField);
      assertNotNull(message);
    }, "ObjectMapper should ignore unknown properties like 'form' in elicitation");
  }

  @Test
  void objectMapper_can_deserialize_initialize_request_with_elicitation_capabilities() {
    var objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    var mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);

    // Test with standard elicitation capabilities (without unknown fields)
    var standardInitializeRequest = """
      {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2024-11-05",
          "capabilities": {
            "elicitation": {}
          },
          "clientInfo": {
            "name": "test-client",
            "version": "1.0.0"
          }
        }
      }
      """;

    // This should work fine with standard message
    assertDoesNotThrow(() -> {
      var message = McpSchema.deserializeJsonRpcMessage(mcpJsonMapper, standardInitializeRequest);
      assertNotNull(message);
    }, "ObjectMapper should deserialize standard initialize request");
  }

}

