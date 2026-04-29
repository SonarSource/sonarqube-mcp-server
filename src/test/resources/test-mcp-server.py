#!/usr/bin/env python3
#
# SonarQube MCP Server
# Copyright (C) SonarSource
# mailto:info AT sonarsource DOT com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the Sonar Source-Available License for more details.
#
# You should have received a copy of the Sonar Source-Available License
# along with this program; if not, see https://sonarsource.com/license/ssal/
#

"""
Simple MCP Server for testing proxied MCP server integration.
This server provides a few test tools that can be discovered and executed.
"""

import argparse
import json
import sys
import os

INSTRUCTIONS = None

def send_message(message):
    """Send a JSON-RPC message to stdout"""
    output = json.dumps(message)
    sys.stdout.write(output + "\n")
    sys.stdout.flush()


def receive_message():
    """Receive a JSON-RPC message from stdin"""
    try:
        line = sys.stdin.readline()
        if not line:
            return None
        return json.loads(line.strip())
    except Exception:
        return None


def handle_initialize(request):
    """Handle the initialize request"""
    # Optionally capture the _meta field to a file (for tests that assert on startup meta).
    capture_path = os.environ.get("TEST_CAPTURE_INIT_META_PATH")
    if capture_path:
        params = request.get("params") or {}
        meta = params.get("_meta")
        try:
            with open(capture_path, "w") as f:
                json.dump(meta if meta is not None else {}, f)
        except Exception:
            pass

    result = {
        "protocolVersion": "2024-11-05",
        "capabilities": {
            "tools": {}
        },
        "serverInfo": {
            "name": "test-mcp-server",
            "version": "1.0.0"
        }
    }
    if INSTRUCTIONS:
        result["instructions"] = INSTRUCTIONS
    send_message({
        "jsonrpc": "2.0",
        "id": request.get("id"),
        "result": result
    })


def handle_list_tools(request):
    """Handle the tools/list request"""
    # Read environment variable to test environment inheritance
    test_env_var = os.environ.get("TEST_ENV_VAR", "not_set")
    
    tools = [
        {
            "name": "test_tool_1",
            "title": "Test Tool 1",
            "description": f"A test tool that returns environment variable: {test_env_var}",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "input": {
                        "type": "string",
                        "description": "Test input"
                    }
                },
                "required": []
            }
        },
        {
            "name": "test_tool_2",
            "title": "Test Tool 2",
            "description": "Another test tool",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "value": {
                        "type": "number",
                        "description": "A numeric value"
                    }
                },
                "required": ["value"]
            }
        }
    ]
    
    send_message({
        "jsonrpc": "2.0",
        "id": request.get("id"),
        "result": {
            "tools": tools
        }
    })


def handle_call_tool(request):
    """Handle the tools/call request"""
    params = request.get("params", {})
    tool_name = params.get("name")
    arguments = params.get("arguments", {})

    if tool_name == "test_tool_1":
        input_value = arguments.get("input", "no input")
        test_env_var = os.environ.get("TEST_ENV_VAR", "not_set")
        result = {
            "isError": False,
            "content": [
                {
                    "type": "text",
                    "text": f"Test Tool 1 executed with input: {input_value}, ENV: {test_env_var}"
                }
            ]
        }
    elif tool_name == "test_tool_2":
        value = arguments.get("value", 0)
        result = {
            "isError": False,
            "content": [
                {
                    "type": "text",
                    "text": f"Test Tool 2 executed with value: {value}"
                }
            ]
        }
    else:
        result = {
            "isError": True,
            "content": [
                {
                    "type": "text",
                    "text": f"Unknown tool: {tool_name}"
                }
            ]
        }
    
    send_message({
        "jsonrpc": "2.0",
        "id": request.get("id"),
        "result": result
    })


def main():
    """Main server loop"""
    while True:
        message = receive_message()
        if message is None:
            break
        
        method = message.get("method")
        
        if method == "initialize":
            handle_initialize(message)
        elif method == "tools/list":
            handle_list_tools(message)
        elif method == "tools/call":
            handle_call_tool(message)
        elif method == "notifications/initialized":
            # Acknowledge initialized notification (no response needed)
            pass
        else:
            # Unknown method - send error
            send_message({
                "jsonrpc": "2.0",
                "id": message.get("id"),
                "error": {
                    "code": -32601,
                    "message": f"Method not found: {method}"
                }
            })


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--instructions", help="Instructions to include in initialize response", default=None)
    args = parser.parse_args()
    INSTRUCTIONS = args.instructions
    main()
