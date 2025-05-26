/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.harness;

import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.google.protobuf.Message;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ProtobufUtils {

  private ProtobufUtils() {
    // utility class
  }

  public static Body protobufBody(Message message) {
    var baos = new ByteArrayOutputStream();
    try {
      message.writeTo(baos);
      return Body.ofBinaryOrText(baos.toByteArray(), ContentTypeHeader.absent());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Body protobufBodyDelimited(Message... messages) {
    var baos = new ByteArrayOutputStream();
    try {
      for (var message : messages) {
        message.writeDelimitedTo(baos);
      }
      return Body.ofBinaryOrText(baos.toByteArray(), ContentTypeHeader.absent());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
