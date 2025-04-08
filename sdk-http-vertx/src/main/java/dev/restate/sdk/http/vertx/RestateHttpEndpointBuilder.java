// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.http.vertx;

import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.endpoint.RequestIdentityVerifier;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import java.util.*;

/**
 * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This will
 *     be removed in the next minor release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class RestateHttpEndpointBuilder {

  private HttpServerOptions options;
  private final Endpoint.Builder endpointBuilder;

  private RestateHttpEndpointBuilder(Vertx vertx) {
    this.options = null;
    this.endpointBuilder = Endpoint.builder();
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public static RestateHttpEndpointBuilder builder() {
    return new RestateHttpEndpointBuilder(Vertx.vertx());
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder withOptions(HttpServerOptions options) {
    this.options = Objects.requireNonNull(options);
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder bind(Object service) {
    this.endpointBuilder.bind(service);
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder bind(ServiceDefinition serviceDefinition) {
    this.endpointBuilder.bind(serviceDefinition);
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder withOpenTelemetry(OpenTelemetry openTelemetry) {
    this.endpointBuilder.withOpenTelemetry(openTelemetry);
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder withRequestIdentityVerifier(
      RequestIdentityVerifier requestIdentityVerifier) {
    this.endpointBuilder.withRequestIdentityVerifier(requestIdentityVerifier);
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public RestateHttpEndpointBuilder enablePreviewContext() {
    this.endpointBuilder.enablePreviewContext();
    return this;
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public int buildAndListen(int port) {
    return RestateHttpServer.listen(endpointBuilder, port);
  }

  /**
   * @deprecated Use {@link RestateHttpServer} in combination with {@link Endpoint} instead. This
   *     will be removed in the next minor release.
   */
  @Deprecated(since = "2.0", forRemoval = true)
  public int buildAndListen() {
    return RestateHttpServer.listen(endpointBuilder);
  }
}
