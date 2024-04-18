// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import dev.restate.sdk.common.BindableService;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import io.opentelemetry.api.OpenTelemetry;

/** Endpoint builder for a Restate AWS Lambda Endpoint, to serve Restate service. */
public final class RestateLambdaEndpointBuilder {

  private final RestateEndpoint.Builder restateEndpoint =
      RestateEndpoint.newBuilder(DeploymentManifestSchema.ProtocolMode.REQUEST_RESPONSE);
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();

  /**
   * Add a Restate service to the endpoint, specifying the {@code executor} where to run the entity
   * code.
   */
  public RestateLambdaEndpointBuilder bind(Object service) {
    return this.bind(RestateEndpoint.discoverBindableServiceFactory(service).create(service));
  }

  /** Add a Restate bindable service to the endpoint. */
  public RestateLambdaEndpointBuilder bind(BindableService<?> service) {
    for (ServiceDefinition<?> serviceDefinition : service.definitions()) {
      //noinspection unchecked
      this.restateEndpoint.bind((ServiceDefinition<Object>) serviceDefinition, service.options());
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
    return new RestateLambdaEndpoint(this.restateEndpoint.build(), this.openTelemetry);
  }
}
