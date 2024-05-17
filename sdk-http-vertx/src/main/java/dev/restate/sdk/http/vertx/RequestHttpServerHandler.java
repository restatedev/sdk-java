// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import static dev.restate.sdk.core.ServiceProtocol.selectSupportedServiceDiscoveryProtocolVersion;
import static dev.restate.sdk.core.ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.ResolvedEndpointHandler;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.ServiceProtocol;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
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

    // check protocol version
    final String protocolVersionString = request.getHeader(CONTENT_TYPE);

    final Protocol.ServiceProtocolVersion serviceProtocolVersion =
        ServiceProtocol.parseServiceProtocolVersion(protocolVersionString);

    if (!ServiceProtocol.is_supported(serviceProtocolVersion)) {
      final String errorMessage =
          String.format(
              "Service endpoint does not support the service protocol version '%s'.",
              protocolVersionString);
      LOG.warn(errorMessage);
      request
          .response()
          .setStatusCode(UNSUPPORTED_MEDIA_TYPE.code())
          .putHeader(CONTENT_TYPE, "text/plain")
          .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
          .end(errorMessage);
      return;
    }

    // Parse request
    String[] pathSegments = SLASH.split(uri.getPath());
    if (pathSegments.length < 3) {
      LOG.warn(
          "Path doesn't match the pattern /invoke/ServiceName/HandlerName nor /discover: '{}'",
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
              serviceName,
              handlerName,
              request::getHeader,
              otelContext,
              ContextualData::put,
              currentContextExecutor(vertxCurrentContext));
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the handler", e);
      request.response().setStatusCode(e.getCode()).end();
      return;
    }

    LOG.debug("Handling request to " + serviceName + "/" + handlerName);

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(OK.code());
    response
        .putHeader(
            CONTENT_TYPE,
            ServiceProtocol.serviceProtocolVersionToHeaderValue(serviceProtocolVersion))
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
    final String acceptVersionsString = request.getHeader(ACCEPT);

    final Discovery.ServiceDiscoveryProtocolVersion serviceDiscoveryProtocolVersion =
        selectSupportedServiceDiscoveryProtocolVersion(acceptVersionsString);

    if (serviceDiscoveryProtocolVersion
        == Discovery.ServiceDiscoveryProtocolVersion
            .SERVICE_DISCOVERY_PROTOCOL_VERSION_UNSPECIFIED) {
      final String errorMessage =
          String.format(
              "Unsupported service discovery protocol version: '%s'", acceptVersionsString);
      LOG.warn(errorMessage);
      request
          .response()
          .setStatusCode(UNSUPPORTED_MEDIA_TYPE.code())
          .putHeader(CONTENT_TYPE, "text/plain")
          .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
          .end(errorMessage);
    } else {
      // Compute response and write it back
      EndpointManifestSchema response = this.restateEndpoint.handleDiscoveryRequest();

      Buffer responseBuffer;
      try {
        responseBuffer =
            Buffer.buffer(
                new ServiceProtocol.DiscoveryResponseSerializer(serviceDiscoveryProtocolVersion)
                    .serialize(response));
      } catch (Exception e) {
        LOG.warn("Error when writing out the manifest POJO", e);
        request.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end(e.getMessage());
        return;
      }

      request
          .response()
          .setStatusCode(OK.code())
          .putHeader(X_RESTATE_SERVER_KEY, X_RESTATE_SERVER_VALUE)
          .putHeader(
              CONTENT_TYPE,
              serviceDiscoveryProtocolVersionToHeaderValue(serviceDiscoveryProtocolVersion))
          .end(responseBuffer);
    }
  }
}
