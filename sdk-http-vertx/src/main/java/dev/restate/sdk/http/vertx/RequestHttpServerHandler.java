// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.ResolvedEndpointHandler;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.version.Version;
import io.netty.util.AsciiString;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.reactiverse.contextual.logging.ContextualData;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

class RequestHttpServerHandler implements Handler<HttpServerRequest> {

  private static final Logger LOG = LogManager.getLogger(RequestHttpServerHandler.class);

  private static final AsciiString X_RESTATE_SERVER_KEY = AsciiString.cached("x-restate-server");
  private static final AsciiString X_RESTATE_SERVER_VALUE =
      AsciiString.cached(Version.X_RESTATE_SERVER);

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));

  private static final String DISCOVER_PATH = "/discover";
  private static final String HEALTH_PATH = "/health";

  static final TextMapGetter<MultiMap> OTEL_TEXT_MAP_GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(MultiMap carrier) {
          return carrier.names();
        }

        @Nullable
        @Override
        public String get(@Nullable MultiMap carrier, String key) {
          if (carrier == null) {
            return null;
          }
          return carrier.get(key);
        }
      };

  private final RestateEndpoint restateEndpoint;
  private final OpenTelemetry openTelemetry;

  RequestHttpServerHandler(RestateEndpoint restateEndpoint, OpenTelemetry openTelemetry) {
    this.restateEndpoint = restateEndpoint;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void handle(HttpServerRequest request) {
    URI uri = URI.create(request.uri());

    // health check
    if (HEALTH_PATH.equalsIgnoreCase(uri.getPath())) {
      this.handleHealthRequest(request);
      return;
    }

    // Discovery request
    if (DISCOVER_PATH.equalsIgnoreCase(uri.getPath())) {
      this.handleDiscoveryRequest(request);
      return;
    }

    // Parse request
    String[] pathSegments = SLASH.split(uri.getPath());
    if (pathSegments.length < 3) {
      LOG.warn(
          "Path doesn't match the pattern /invoke/ServiceName/HandlerName nor /discover nor /health: '{}'",
          request.path());
      request.response().setStatusCode(NOT_FOUND.code()).end();
      return;
    }
    String serviceName = pathSegments[pathSegments.length - 2];
    String handlerName = pathSegments[pathSegments.length - 1];

    // Parse OTEL context and generate span
    final io.opentelemetry.context.Context otelContext =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(
                io.opentelemetry.context.Context.current(),
                request.headers(),
                OTEL_TEXT_MAP_GETTER);

    Context vertxCurrentContext = ((HttpServerRequestInternal) request).context();

    ResolvedEndpointHandler handler;
    try {
      handler =
          restateEndpoint.resolve(
              request.getHeader(CONTENT_TYPE),
              serviceName,
              handlerName,
              request::getHeader,
              otelContext,
              ContextualData::put,
              currentContextExecutor(vertxCurrentContext));
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

    LOG.debug("Handling request to {}/{}", serviceName, handlerName);

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(OK.code());
    response
        .putHeader(CONTENT_TYPE, handler.responseContentType())
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE);
    // This is No-op for HTTP2
    response.setChunked(true);

    HttpRequestFlowAdapter requestFlowAdapter = new HttpRequestFlowAdapter(request);
    HttpResponseFlowAdapter responseFlowAdapter = new HttpResponseFlowAdapter(response);

    requestFlowAdapter.subscribe(handler);
    handler.subscribe(responseFlowAdapter);
  }

  private Executor currentContextExecutor(Context currentContext) {
    return runnable -> currentContext.runOnContext(v -> runnable.run());
  }

  private void handleDiscoveryRequest(HttpServerRequest request) {
    RestateEndpoint.DiscoveryResponse discoveryResponse;
    try {
      discoveryResponse = restateEndpoint.handleDiscoveryRequest(request.getHeader(ACCEPT));
    } catch (ProtocolException e) {
      LOG.warn("Error when handling the discovery request", e);
      request
          .response()
          .setStatusCode(e.getCode())
          .putHeader(CONTENT_TYPE, "text/plain")
          .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
          .end(e.getMessage());
      return;
    }

    request
        .response()
        .setStatusCode(OK.code())
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
        .putHeader(CONTENT_TYPE, discoveryResponse.getContentType())
        .end(Buffer.buffer(discoveryResponse.getSerializedManifest()));
  }

  private void handleHealthRequest(HttpServerRequest request) {
    request
        .response()
        .setStatusCode(OK.code())
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
        .end();
  }
}
