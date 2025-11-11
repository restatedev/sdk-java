// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.RequestProcessor;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.version.Version;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.Nullable;
import org.springframework.web.HttpRequestHandler;

/** Spring {@link HttpRequestHandler} adapter for {@link Endpoint}. */
public class RestateHttpHandlerAdapter implements HttpRequestHandler {

  private static final Logger LOG = LogManager.getLogger(RestateHttpHandlerAdapter.class);
  private static final String X_RESTATE_SERVER_HEADER = "x-restate-server";
  private static final String X_RESTATE_SERVER_VALUE =
      "restate-sdk-java-spring/" + Version.VERSION + "_" + Version.GIT_HASH;

  private final EndpointRequestHandler endpoint;
  private final boolean enableBidirectionalStreaming;

  public RestateHttpHandlerAdapter(Endpoint endpoint, boolean enableBidirectionalStreaming) {
    this.endpoint = EndpointRequestHandler.create(endpoint);
    this.enableBidirectionalStreaming = enableBidirectionalStreaming;
  }

  @Override
  public void handleRequest(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setHeader(X_RESTATE_SERVER_HEADER, X_RESTATE_SERVER_VALUE);

    RequestProcessor requestProcessor;
    try {
      requestProcessor =
          this.endpoint.processorForRequest(
                  request.getRequestURI(),
              new HeadersAccessor() {
                @Override
                public Iterable<String> keys() {
                  return Collections.list(request.getHeaderNames());
                }

                @Override
                public @Nullable String get(String key) {
                  return request.getHeader(key);
                }
              },
              ThreadContext::put,
                  Executors.newSingleThreadExecutor(),
              enableBidirectionalStreaming
                  && ("HTTP/2.0".equals(request.getProtocol())
                      || "HTTP/2".equals(request.getProtocol())));
    } catch (Exception e) {
      LOG.warn("Error when handling the request", e);
      response.setStatus((e instanceof ProtocolException protocolException) ? protocolException.getCode() : 500);
      response.setContentType("text/plain");
      response.getWriter().write(e.getMessage());
      return;
    }

    // Set response headers
    response.setStatus(requestProcessor.statusCode());
    response.setContentType(requestProcessor.responseContentType());

    // Start async processing for streaming support
    AsyncContext asyncContext = request.startAsync();
    asyncContext.setTimeout(0); // No timeout
      asyncContext.start(() -> {
          // Create flow adapters
          ServletRequestFlowAdapter requestFlowAdapter =
                  new ServletRequestFlowAdapter(request, asyncContext);
          ServletResponseFlowAdapter responseFlowAdapter =
                  new ServletResponseFlowAdapter(response, asyncContext);

          // Wire up the flow
          requestFlowAdapter.subscribe(requestProcessor);
          requestProcessor.subscribe(responseFlowAdapter);
      });
  }
}
