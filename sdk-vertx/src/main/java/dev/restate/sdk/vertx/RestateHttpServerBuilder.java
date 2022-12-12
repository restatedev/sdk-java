package dev.restate.sdk.vertx;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.BindableNonBlockingService;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.Arrays;
import java.util.HashSet;

public class RestateHttpServerBuilder {

  private final Vertx vertx;
  private final RestateGrpcServer.Builder restateGrpcServerBuilder = RestateGrpcServer.newBuilder();
  private final HashSet<String> blockingServices = new HashSet<>();
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();
  private HttpServerOptions options;

  private RestateHttpServerBuilder(Vertx vertx) {
    this.vertx = vertx;
  }

  public RestateHttpServerBuilder withOptions(HttpServerOptions options) {
    this.options = options;
    return this;
  }

  public RestateHttpServerBuilder withService(
      BindableBlockingService service, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(service, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    this.blockingServices.add(definition.getServiceDescriptor().getName());
    return this;
  }

  public RestateHttpServerBuilder withService(
      BindableNonBlockingService service, ServerInterceptor... interceptors) {
    this.restateGrpcServerBuilder.withService(
        ServerInterceptors.intercept(service, Arrays.asList(interceptors)));
    return this;
  }

  public RestateHttpServerBuilder withSerde(Serde... serde) {
    this.restateGrpcServerBuilder.withSerde(serde);
    return this;
  }

  public RestateHttpServerBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
  }

  public Future<HttpServer> buildAndListen() {
    return build().listen(8080);
  }

  public Future<HttpServer> buildAndListen(int port) {
    return build().listen(port);
  }

  public HttpServer build() {
    HttpServer server =
        (options != null) ? vertx.createHttpServer(options) : vertx.createHttpServer();

    this.restateGrpcServerBuilder.withTracer(
        this.openTelemetry.getTracer("restate-java-sdk-vertx"));

    server.requestHandler(
        new RequestServerHandler(
            vertx, this.restateGrpcServerBuilder.build(), blockingServices, openTelemetry));

    return server;
  }

  public static RestateHttpServerBuilder builder(Vertx vertx) {
    return new RestateHttpServerBuilder(vertx);
  }
}
