// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.lambda;

import dev.restate.sdk.endpoint.RequestIdentityVerifier;
import dev.restate.sdk.endpoint.ServiceDefinition;
import dev.restate.sdk.core.RestateEndpoint;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import io.opentelemetry.api.OpenTelemetry;

/** Endpoint builder for a Restate AWS Lambda Endpoint, to serve Restate service. */
public final class RestateLambdaEndpointBuilder {

  private final RestateEndpoint.Builder restateEndpoint =
      RestateEndpoint.newBuilder(EndpointManifestSchema.ProtocolMode.REQUEST_RESPONSE);
  private OpenTelemetry openTelemetry = OpenTelemetry.noop();

  /**
   * Add a Restate service to the endpoint. This will automatically discover the generated factory
   * based on the class name.
   *
   * <p>You can also manually instantiate the {@link ServiceDefinition} using {@link
   * #bind(ServiceDefinition)}.
   */
  public RestateLambdaEndpointBuilder bind(Object service) {
    return this.bind(RestateEndpoint.discoverServiceDefinitionFactory(service).create(service));
  }

  /**
   * Add a Restate service to the endpoint.
   *
   * <p>To set the options, use {@link #bind(ServiceDefinition, Object)}.
   */
  public RestateLambdaEndpointBuilder bind(ServiceDefinition<?> service) {
    //noinspection unchecked
    this.restateEndpoint.bind((ServiceDefinition<Object>) service, null);
    return this;
  }

  /** Add a Restate service to the endpoint, setting the options. */
  public <O> RestateLambdaEndpointBuilder bind(ServiceDefinition<O> serviceDefinition, O options) {
    this.restateEndpoint.bind(serviceDefinition, options);
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

  /**
   * Set the request identity verifier for this endpoint.
   *
   * <p>For the Restate implementation to use with Restate Cloud, check the module {@code
   * sdk-request-identity}.
   */
  public RestateLambdaEndpointBuilder withRequestIdentityVerifier(
      RequestIdentityVerifier requestIdentityVerifier) {
    this.restateEndpoint.withRequestIdentityVerifier(requestIdentityVerifier);
    return this;
  }

  public RestateLambdaEndpointBuilder enablePreviewContext() {
    this.restateEndpoint.enablePreviewContext();
    return this;
  }

  /** Build the {@link RestateLambdaEndpoint} serving the Restate service endpoint. */
  public RestateLambdaEndpoint build() {
    return new RestateLambdaEndpoint(this.restateEndpoint.build(), this.openTelemetry);
  }
}
