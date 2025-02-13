// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceDefinitionAndOptions;
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactories;
import io.opentelemetry.api.OpenTelemetry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Restate endpoint, encapsulating the configured services, together with additional options. */
public final class Endpoint {

  private final Map<String, ServiceDefinitionAndOptions<?>> services;
  private final OpenTelemetry openTelemetry;
  private final RequestIdentityVerifier requestIdentityVerifier;
  private final boolean experimentalContextEnabled;

  private Endpoint(
      Map<String, ServiceDefinitionAndOptions<?>> services,
      OpenTelemetry openTelemetry,
      RequestIdentityVerifier requestIdentityVerifier,
      boolean experimentalContextEnabled) {
    this.services = services;
    this.openTelemetry = openTelemetry;
    this.requestIdentityVerifier = requestIdentityVerifier;
    this.experimentalContextEnabled = experimentalContextEnabled;
  }

  public static class Builder {
    private final List<ServiceDefinitionAndOptions<?>> services = new ArrayList<>();
    private RequestIdentityVerifier requestIdentityVerifier = RequestIdentityVerifier.noop();
    private OpenTelemetry openTelemetry = OpenTelemetry.noop();
    private boolean experimentalContextEnabled = false;

    /**
     * Add a Restate service to the endpoint. This will automatically discover the generated factory
     * based on the class name.
     *
     * <p>You can also manually instantiate the {@link ServiceDefinition} using {@link
     * #bind(ServiceDefinition)}.
     */
    public Builder bind(Object service) {
      return this.bind(ServiceDefinitionFactories.discover(service).create(service));
    }

    /**
     * Add a Restate service to the endpoint.
     *
     * <p>To set the options, use {@link #bind(ServiceDefinition, Object)}.
     */
    public Builder bind(ServiceDefinition<?> serviceDefinition) {
      //noinspection unchecked
      return this.bind((ServiceDefinition<Object>) serviceDefinition, null);
    }

    /** Add a Restate service to the endpoint, setting the options. */
    public <O> Builder bind(ServiceDefinition<O> serviceDefinition, O options) {
      this.services.add(new ServiceDefinitionAndOptions<>(serviceDefinition, options));
      return this;
    }

    /**
     * Set the {@link OpenTelemetry} implementation for tracing and metrics.
     *
     * @see OpenTelemetry
     */
    public Builder withOpenTelemetry(OpenTelemetry openTelemetry) {
      this.openTelemetry = openTelemetry;
      return this;
    }

    /** Same as {@link #withOpenTelemetry(OpenTelemetry)}. */
    public void setOpenTelemetry(OpenTelemetry openTelemetry) {
      withOpenTelemetry(openTelemetry);
    }

    /**
     * @return the configured {@link OpenTelemetry}
     */
    public OpenTelemetry getOpenTelemetry() {
      return this.openTelemetry;
    }

    /**
     * Set the request identity verifier for this endpoint.
     *
     * <p>For the Restate implementation to use with Restate Cloud, check the module {@code
     * sdk-request-identity}.
     */
    public Builder withRequestIdentityVerifier(RequestIdentityVerifier requestIdentityVerifier) {
      this.requestIdentityVerifier = requestIdentityVerifier;
      return this;
    }

    /** Same as {@link #withRequestIdentityVerifier(RequestIdentityVerifier)}. */
    public void setRequestIdentityVerifier(RequestIdentityVerifier requestIdentityVerifier) {
      this.withRequestIdentityVerifier(requestIdentityVerifier);
    }

    /**
     * @return the configured request identity verifier
     */
    public RequestIdentityVerifier getRequestIdentityVerifier() {
      return this.requestIdentityVerifier;
    }

    public Builder enablePreviewContext() {
      this.experimentalContextEnabled = true;
      return this;
    }

    public Endpoint build() {
      return new Endpoint(
          this.services.stream()
              .collect(Collectors.toMap(c -> c.service().getServiceName(), Function.identity())),
          this.openTelemetry,
          this.requestIdentityVerifier,
          this.experimentalContextEnabled);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * @see Builder#bind(Object)
   */
  public static Builder bind(Object object) {
    return new Builder().bind(object);
  }

  public ServiceDefinitionAndOptions<?> resolveServiceAndOptions(String serviceName) {
    return services.get(serviceName);
  }

  public Stream<ServiceDefinition<?>> getServiceDefinitions() {
    return this.services.values().stream().map(ServiceDefinitionAndOptions::service);
  }

  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  public RequestIdentityVerifier getRequestIdentityVerifier() {
    return requestIdentityVerifier;
  }

  public boolean isExperimentalContextEnabled() {
    return experimentalContextEnabled;
  }
}
