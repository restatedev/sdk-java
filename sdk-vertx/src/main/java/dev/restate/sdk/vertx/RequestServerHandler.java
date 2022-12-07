package dev.restate.sdk.vertx;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.*;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpServerRequestInternal;
import java.net.URI;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class RequestServerHandler implements Handler<HttpServerRequest> {

  private static final Logger LOG = LogManager.getLogger(RequestServerHandler.class);

  private static final AsciiString APPLICATION_RESTATE = AsciiString.cached("application/restate");

  private static final Pattern SLASH = Pattern.compile(Pattern.quote("/"));

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

  private final Vertx vertx;
  private final RestateGrpcServer restateGrpcServer;
  private final HashSet<String> blockingServices;
  private final OpenTelemetry openTelemetry;

  RequestServerHandler(
      Vertx vertx,
      RestateGrpcServer restateGrpcServer,
      HashSet<String> blockingServices,
      OpenTelemetry openTelemetry) {
    this.vertx = vertx;
    this.restateGrpcServer = restateGrpcServer;
    this.blockingServices = blockingServices;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void handle(HttpServerRequest request) {
    URI uri = URI.create(request.uri());
    String[] pathSegments = SLASH.split(uri.getPath());

    // Parse request
    if (pathSegments.length < 3) {
      LOG.warn("Path doesn't match the pattern /invoke/SvcName/MethodName: '{}'", request.path());
      request.response().setStatusCode(404).end();
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
              isBlockingService
                  ? blockingSyscallsTrampoline(vertxCurrentContext)
                  : Function.identity(),
              isBlockingService ? blockingServerCallListenerTrampoline() : Function.identity());
    } catch (ProtocolException e) {
      LOG.warn("Error when resolving the grpc handler", e);
      request.response().setStatusCode(e.getGrpcCode() == Status.Code.NOT_FOUND ? 404 : 500).end();
      return;
    }

    // Prepare the header frame to send in the response.
    // Vert.x will send them as soon as we send the first write
    HttpServerResponse response = request.response();
    response.setStatusCode(HttpResponseStatus.OK.code());
    response.putHeader(HttpHeaderNames.CONTENT_TYPE, APPLICATION_RESTATE);
    // This is No-op for HTTP2
    response.setChunked(true);

    HttpRequestFlowAdapter requestFlowAdapter = new HttpRequestFlowAdapter(request);
    HttpResponseFlowAdapter responseFlowAdapter = new HttpResponseFlowAdapter(response);

    requestFlowAdapter.subscribe(handler.processor());
    handler.processor().subscribe(responseFlowAdapter);

    handler.start();
  }

  private Function<SyscallsInternal, SyscallsInternal> blockingSyscallsTrampoline(
      Context currentContext) {
    return TrampolineFactories.syscalls(currentContextExecutor(currentContext));
  }

  private Function<ServerCall.Listener<MessageLite>, ServerCall.Listener<MessageLite>>
      blockingServerCallListenerTrampoline() {
    return TrampolineFactories.serverCallListener(blockingExecutor());
  }

  private Executor currentContextExecutor(Context currentContext) {
    return runnable -> currentContext.runOnContext(v -> runnable.run());
  }

  private Executor blockingExecutor() {
    return runnable ->
        vertx.executeBlocking(
            promise -> {
              try {
                runnable.run();
              } catch (Throwable e) {
                promise.fail(e);
                return;
              }
              promise.complete();
            });
  }
}
