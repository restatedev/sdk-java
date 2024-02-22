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
import dev.restate.sdk.common.BlockingComponent;
import dev.restate.sdk.common.Component;
import dev.restate.sdk.common.ComponentAdapter;
import dev.restate.sdk.core.RestateEndpoint;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;
import java.util.List;

/** Endpoint builder for a Restate AWS Lambda Endpoint, to serve Restate service. */
public final class RestateLambdaEndpointBuilder {

  private final RestateEndpoint.Builder restateGrpcServerBuilder =
      RestateEndpoint.newBuilder(Discovery.ProtocolMode.REQUEST_RESPONSE);
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();

  /**
   * Add a {@link Component} to the endpoint.
   *
   * <p>The service code will be executed on the same thread where the lambda is invoked.
   */
  public RestateLambdaEndpointBuilder withService(
      Component component, ServerInterceptor... interceptors) {
    ServerServiceDefinition definition =
        ServerInterceptors.intercept(component, Arrays.asList(interceptors));
    this.restateGrpcServerBuilder.withService(definition);
    return this;
  }

  /**
   * Add a Restate entity to the endpoint, specifying the {@code executor} where to run the entity
   * code.
   */
  public RestateLambdaEndpointBuilder with(Object service) {
    return this.with(service, RestateEndpoint.discoverAdapter(service));
  }

  public <T> RestateLambdaEndpointBuilder with(T service, ComponentAdapter<T> adapter) {
    List<BlockingComponent> services = adapter.adapt(service).components();
    for (Component svc : services) {
      this.restateGrpcServerBuilder.withService(svc);
    }

    return this;
  }

  /**
   * Add a {@link OpenTelemetry} implementation for tracing and metrics.
   *
   * @see OpenTelemetry
   */
  public RestateLambdaEndpointBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    return this;
  }

  /** Build the {@link RestateLambdaEndpoint} serving the Restate service endpoint. */
  public RestateLambdaEndpoint build() {
    return new RestateLambdaEndpoint(this.restateGrpcServerBuilder.build(), this.openTelemetry);
  }
}
