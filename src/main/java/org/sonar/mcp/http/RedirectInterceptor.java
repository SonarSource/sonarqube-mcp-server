/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.http;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

class RedirectInterceptor implements HttpResponseInterceptor {

  @Override
  public void process(HttpResponse response, EntityDetails entity, HttpContext context) {
    alterResponseCodeIfNeeded(context, response);
  }

  private static void alterResponseCodeIfNeeded(HttpContext context, HttpResponse response) {
    if (isPost(context)) {
      // Apache handles some redirect statuses by transforming the POST into a GET
      // we force a different status to keep the request a POST
      var code = response.getCode();
      if (code == HttpStatus.SC_MOVED_PERMANENTLY) {
        response.setCode(HttpStatus.SC_PERMANENT_REDIRECT);
      } else if (code == HttpStatus.SC_MOVED_TEMPORARILY || code == HttpStatus.SC_SEE_OTHER) {
        response.setCode(HttpStatus.SC_TEMPORARY_REDIRECT);
      }
    }
  }

  private static boolean isPost(HttpContext context) {
    var httpCoreContext = HttpCoreContext.cast(context);
    var request = httpCoreContext.getRequest();
    return request != null && Method.POST.isSame(request.getMethod());
  }

}
