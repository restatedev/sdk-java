package dev.restate.sdk.http.vertx;

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
import io.reactiverse.contextual.logging.ContextualData;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import java.net.URI;
import java.util.HashMap;
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
  private final HashMap<String, Executor> blockingServices;
  private final OpenTelemetry openTelemetry;

  RequestHttpServerHandler(
      RestateGrpcServer restateGrpcServer,
      HashMap<String, Executor> blockingServices,
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
    String serviceName = pathSegments[pathSegments.length - 2];
    String methodName = pathSegments[pathSegments.length - 1];
    boolean isBlockingService = blockingServices.containsKey(serviceName);

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
              serviceName,
              methodName,
              otelContext,
              new RestateGrpcServer.LoggingContextSetter() {
                @Override
                public void setServiceMethod(String serviceMethod) {
                  ContextualData.put(
                      RestateGrpcServer.LoggingContextSetter.SERVICE_METHOD_KEY, serviceMethod);
                }

                @Override
                public void setInvocationId(String id) {
                  ContextualData.put(RestateGrpcServer.LoggingContextSetter.INVOCATION_ID_KEY, id);
                }
              },
              isBlockingService ? currentContextExecutor(vertxCurrentContext) : null,
              isBlockingService ? blockingExecutor(serviceName) : null);
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      request
          .response()
          .setStatusCode(
              e.getFailureCode() == Status.Code.NOT_FOUND.value()
                  ? NOT_FOUND.code()
                  : INTERNAL_SERVER_ERROR.code())
          .end();
      return;
    }

    LOG.debug("Handling request to " + serviceName + "/" + methodName);

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

  private Executor blockingExecutor(String serviceName) {
    Executor userExecutor = this.blockingServices.get(serviceName);
    return runnable -> {
      // We need to propagate the gRPC context!
      io.grpc.Context ctx = io.grpc.Context.current();
      userExecutor.execute(ctx.wrap(runnable));
    };
  }

  private void handleDiscoveryRequest(HttpServerRequest request) {
    // Request validation
    if (!request.method().equals(HttpMethod.POST)) {
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
