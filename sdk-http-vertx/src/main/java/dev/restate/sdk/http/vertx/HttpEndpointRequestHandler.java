// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

import dev.restate.sdk.core.EndpointRequestHandler;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.RequestProcessor;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.HeadersAccessor;
import dev.restate.sdk.version.Version;
import io.netty.util.AsciiString;
import io.reactiverse.contextual.logging.ContextualData;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import java.net.URI;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** Vert.x HttpServer handler adapter for {@link Endpoint}. See {@link #fromEndpoint(Endpoint)}. */
public class HttpEndpointRequestHandler implements Handler<HttpServerRequest> {

  private static final Logger LOG = LogManager.getLogger(HttpEndpointRequestHandler.class);

  private static final AsciiString X_RESTATE_SERVER_KEY = AsciiString.cached("x-restate-server");
  private static final AsciiString X_RESTATE_SERVER_VALUE =
      AsciiString.cached(Version.X_RESTATE_SERVER);

  private final EndpointRequestHandler endpoint;
  private final boolean enableBidirectionalStreaming;

  private HttpEndpointRequestHandler(Endpoint endpoint, boolean enableBidirectionalStreaming) {
    this.endpoint = EndpointRequestHandler.create(endpoint);
    this.enableBidirectionalStreaming = enableBidirectionalStreaming;
  }

  @Override
  public void handle(HttpServerRequest request) {
    URI uri = URI.create(request.uri());
    Context vertxCurrentContext = ((HttpServerRequestInternal) request).context();

    RequestProcessor requestProcessor;
    try {
      requestProcessor =
          this.endpoint.processorForRequest(
              uri.getPath(),
              new HeadersAccessor() {
                @Override
                public Iterable<String> keys() {
                  return request.headers().names();
                }

                @Override
                public @Nullable String get(String key) {
                  return request.getHeader(key);
                }
              },
              ContextualData::put,
              currentContextExecutor(vertxCurrentContext),
              enableBidirectionalStreaming && request.version() == HttpVersion.HTTP_2);
    } catch (ProtocolException e) {
      LOG.warn("Error when handling the request", e);
      request
          .response()
          .setStatusCode(e.getCode())
          .putHeader(CONTENT_TYPE, "text/plain")
          .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
          .end(e.getMessage());
      return;
    }

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(requestProcessor.statusCode());
    response
        .putHeader(CONTENT_TYPE, requestProcessor.responseContentType())
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE);
    // This is No-op for HTTP2
    response.setChunked(true);

    HttpRequestFlowAdapter requestFlowAdapter = new HttpRequestFlowAdapter(request);
    HttpResponseFlowAdapter responseFlowAdapter = new HttpResponseFlowAdapter(response);

    requestFlowAdapter.subscribe(requestProcessor);
    requestProcessor.subscribe(responseFlowAdapter);
  }

  private Executor currentContextExecutor(Context currentContext) {
    return runnable -> currentContext.runOnContext(v -> runnable.run());
  }

  /**
   * Create a {@link HttpEndpointRequestHandler}
   *
   * @param endpoint the endpoint to wrap
   * @return the built handler
   */
  public static HttpEndpointRequestHandler fromEndpoint(Endpoint endpoint) {
    return new HttpEndpointRequestHandler(endpoint, true);
  }

  /**
   * Create a {@link HttpEndpointRequestHandler}
   *
   * @param endpoint the endpoint to wrap
   * @param disableBidirectionalStreaming if true, disable bidirectional streaming with HTTP/2
   *     requests. Restate initiates for each invocation a bidirectional streaming using HTTP/2
   *     between restate-server and the SDK. In some network setups, for example when using a load
   *     balancers that buffer request/response, bidirectional streaming will not work correctly.
   *     Only in these scenarios, we suggest disabling bidirectional streaming.
   * @return the built handler
   */
  public static HttpEndpointRequestHandler fromEndpoint(
      Endpoint endpoint, boolean disableBidirectionalStreaming) {
    return new HttpEndpointRequestHandler(endpoint, !disableBidirectionalStreaming);
  }
}
