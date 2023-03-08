package dev.restate.sdk.lambda;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.BindableNonBlockingService;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;

/** Endpoint builder for a Restate AWS Lambda Endpoint, to serve Restate service. */
public final class LambdaRestateServerBuilder {

  private final RestateGrpcServer.Builder restateGrpcServerBuilder = RestateGrpcServer.newBuilder();
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();

  /** Add a {@link BindableBlockingService} to the endpoint. */
  public LambdaRestateServerBuilder withService(
      BindableBlockingService service, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(service, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    return this;
  }

  /** Add a {@link BindableNonBlockingService} to the endpoint. */
  public LambdaRestateServerBuilder withService(
      BindableNonBlockingService service, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(service, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    return this;
  }

  /**
   * Add a custom {@link Serde} implementation. Invoking this method will override every serde
   * discovered through SPI.
   *
   * @see Serde
   */
  public LambdaRestateServerBuilder withSerde(Serde... serde) {
    this.restateGrpcServerBuilder.withSerde(serde);
    return this;
  }

  /**
   * Add a {@link OpenTelemetry} implementation for tracing and metrics.
   *
   * @see OpenTelemetry
   */
  public LambdaRestateServerBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
  }

  /** Build the {@link LambdaRestateServer} serving the Restate service endpoint. */
  public LambdaRestateServer build() {
    return new LambdaRestateServer(this.restateGrpcServerBuilder.build(), this.openTelemetry);
  }
}
