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
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.ResolvedEndpointHandler;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
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

  private static final AsciiString APPLICATION_RESTATE = AsciiString.cached("application/restate");
  private static final AsciiString X_RESTATE_SERVER_KEY = AsciiString.cached("x-restate-server");
  private static final AsciiString X_RESTATE_SERVER_VALUE =
      AsciiString.cached(Version.X_RESTATE_SERVER);
  private static final ObjectMapper MANIFEST_OBJECT_MAPPER = new ObjectMapper();

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));

  private static final String DISCOVER_PATH = "/discover";

  static TextMapGetter<MultiMap> OTEL_TEXT_MAP_GETTER =
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

    // Let's first check if it's a discovery request
    if (DISCOVER_PATH.equalsIgnoreCase(uri.getPath())) {
      this.handleDiscoveryRequest(request);
      return;
    }

    // Parse request
    String[] pathSegments = SLASH.split(uri.getPath());
    if (pathSegments.length < 3) {
      LOG.warn(
          "Path doesn't match the pattern /invoke/ComponentName/HandlerName nor /discover: '{}'",
          request.path());
      request.response().setStatusCode(NOT_FOUND.code()).end();
      return;
    }
    String componentName = pathSegments[pathSegments.length - 2];
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
              componentName,
              handlerName,
              otelContext,
              ContextualData::put,
              currentContextExecutor(vertxCurrentContext));
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the handler", e);
      request.response().setStatusCode(e.getCode()).end();
      return;
    }

    LOG.debug("Handling request to " + componentName + "/" + handlerName);

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(OK.code());
    response
        .putHeader(CONTENT_TYPE, APPLICATION_RESTATE)
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE);
    // This is No-op for HTTP2
    response.setChunked(true);

    HttpRequestFlowAdapter requestFlowAdapter = new HttpRequestFlowAdapter(request);
    HttpResponseFlowAdapter responseFlowAdapter = new HttpResponseFlowAdapter(response);

    requestFlowAdapter.subscribe(handler.input());
    handler.output().subscribe(responseFlowAdapter);

    handler.start();
  }

  private Executor currentContextExecutor(Context currentContext) {
    return runnable -> currentContext.runOnContext(v -> runnable.run());
  }

  private void handleDiscoveryRequest(HttpServerRequest request) {
    // Compute response and write it back
    DeploymentManifestSchema response = this.restateEndpoint.handleDiscoveryRequest();
    Buffer responseBuffer;
    try {
      responseBuffer = Buffer.buffer(MANIFEST_OBJECT_MAPPER.writeValueAsBytes(response));
    } catch (JsonProcessingException e) {
      LOG.warn("Error when writing out the manifest POJO", e);
      request.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
      return;
    }

    request
        .response()
        .setStatusCode(OK.code())
        .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .end(responseBuffer);
  }
}
