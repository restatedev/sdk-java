package dev.restate.sdk.vertx;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.impl.*;
import io.grpc.Status;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import java.net.URI;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class RequestHttpServerHandler implements Handler<HttpServerRequest> {

  private static final Logger LOG = LogManager.getLogger(RequestHttpServerHandler.class);

  private static final AsciiString APPLICATION_RESTATE = AsciiString.cached("application/restate");
  private static final String APPLICATION_PROTO = "application/proto";

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));

  private static final String SERVICES_PATH = "/discover";

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

  private final RestateGrpcServer restateGrpcServer;
  private final HashSet<String> blockingServices;
  private final OpenTelemetry openTelemetry;

  RequestHttpServerHandler(
      RestateGrpcServer restateGrpcServer,
      HashSet<String> blockingServices,
      OpenTelemetry openTelemetry) {
    this.restateGrpcServer = restateGrpcServer;
    this.blockingServices = blockingServices;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void handle(HttpServerRequest request) {
    URI uri = URI.create(request.uri());

    // Let's first check if it's a discovery request
    if (SERVICES_PATH.equalsIgnoreCase(uri.getPath())) {
      this.handleDiscoveryRequest(request);
      return;
    }

    // Parse request
    String[] pathSegments = SLASH.split(uri.getPath());
    if (pathSegments.length < 3) {
      LOG.warn("Path doesn't match the pattern /invoke/SvcName/MethodName: '{}'", request.path());
      request.response().setStatusCode(NOT_FOUND.code()).end();
      return;
    }
    String service = pathSegments[pathSegments.length - 2];
    String method = pathSegments[pathSegments.length - 1];
    boolean isBlockingService = blockingServices.contains(service);

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

    InvocationHandler handler;
    try {
      handler =
          restateGrpcServer.resolve(
              service,
              method,
              otelContext,
              isBlockingService ? currentContextExecutor(vertxCurrentContext) : null,
              isBlockingService ? blockingExecutor(vertxCurrentContext) : null);
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      request
          .response()
          .setStatusCode(
              e.getGrpcCode() == Status.Code.NOT_FOUND
                  ? NOT_FOUND.code()
                  : INTERNAL_SERVER_ERROR.code())
          .end();
      return;
    }

    LOG.debug("Handling request to " + service + "/" + method);

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(OK.code());
    response.putHeader(CONTENT_TYPE, APPLICATION_RESTATE);
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

  private Executor blockingExecutor(Context currentContext) {
    return runnable ->
        currentContext.executeBlocking(
            promise -> {
              try {
                runnable.run();
              } catch (Throwable e) {
                promise.fail(e);
                return;
              }
              promise.complete();
            },
            false);
  }

  private void handleDiscoveryRequest(HttpServerRequest request) {
    // Request validation
    if (request.method() != HttpMethod.POST) {
      request.response().setStatusCode(METHOD_NOT_ALLOWED.code()).end();
      return;
    }
    if (!request.getHeader(CONTENT_TYPE).equalsIgnoreCase(APPLICATION_PROTO)) {
      request.response().setStatusCode(BAD_REQUEST.code()).end();
      return;
    }

    // Wait for the request body
    request
        .body()
        .andThen(
            asyncResult -> {
              if (asyncResult.failed()) {
                LOG.warn(
                    "Error when reading the request body of discovery request",
                    asyncResult.cause());
                request.response().setStatusCode(INTERNAL_SERVER_ERROR.code()).end();
                return;
              }

              // Parse request body
              Discovery.ServiceDiscoveryRequest discoveryRequest;
              try {
                discoveryRequest =
                    Discovery.ServiceDiscoveryRequest.parseFrom(
                        asyncResult.result().getByteBuf().nioBuffer());
              } catch (InvalidProtocolBufferException e) {
                LOG.warn("Cannot parse discovery request", e);
                request.response().setStatusCode(BAD_REQUEST.code()).end();
                return;
              }

              // Compute response and write it back
              Discovery.ServiceDiscoveryResponse response =
                  this.restateGrpcServer.handleDiscoveryRequest(discoveryRequest);
              request
                  .response()
                  .setStatusCode(OK.code())
                  .putHeader(CONTENT_TYPE, APPLICATION_PROTO)
                  .end(
                      Buffer.buffer(
                          Unpooled.wrappedBuffer(response.toByteString().asReadOnlyByteBuffer())));
            });
  }
}
