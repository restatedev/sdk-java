// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.BindableNonBlockingService;
import dev.restate.sdk.core.impl.RestateGrpcServer;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;

/** Endpoint builder for a Restate AWS Lambda Endpoint, to serve Restate service. */
public final class LambdaRestateServerBuilder {

  private final RestateGrpcServer.Builder restateGrpcServerBuilder =
      RestateGrpcServer.newBuilder(Discovery.ProtocolMode.REQUEST_RESPONSE);
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
