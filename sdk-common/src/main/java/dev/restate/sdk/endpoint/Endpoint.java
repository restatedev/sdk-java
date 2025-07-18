// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import dev.restate.sdk.endpoint.definition.HandlerRunner;
import dev.restate.sdk.endpoint.definition.ServiceDefinition;
import dev.restate.sdk.endpoint.definition.ServiceDefinitionFactories;
import io.opentelemetry.api.OpenTelemetry;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Restate endpoint, encapsulating the configured services, together with additional options. */
public final class Endpoint {

  private final Map<String, ServiceDefinition> services;
  private final OpenTelemetry openTelemetry;
  private final RequestIdentityVerifier requestIdentityVerifier;
  private final boolean experimentalContextEnabled;

  private Endpoint(
      Map<String, ServiceDefinition> services,
      OpenTelemetry openTelemetry,
      RequestIdentityVerifier requestIdentityVerifier,
      boolean experimentalContextEnabled) {
    this.services = services;
    this.openTelemetry = openTelemetry;
    this.requestIdentityVerifier = requestIdentityVerifier;
    this.experimentalContextEnabled = experimentalContextEnabled;
  }

  public static class Builder {
    private final List<ServiceDefinition> services = new ArrayList<>();
    private RequestIdentityVerifier requestIdentityVerifier = RequestIdentityVerifier.noop();
    private OpenTelemetry openTelemetry = OpenTelemetry.noop();
    private boolean experimentalContextEnabled = false;

    /**
     * Add a Restate service to the endpoint. This will automatically discover the generated factory
     * based on the class name.
     *
     * <p>If you want to modify some of the service definition options, such as documentation,
     * inactivity timeout, and so on, use {@link #bind(Object, Consumer)} instead.
     *
     * <p>You can also manually instantiate the {@link ServiceDefinition} using {@link
     * #bind(ServiceDefinition)}.
     */
    public Builder bind(Object service) {
      return this.bind(service, ignored -> {});
    }

    /**
     * Like {@link #bind(Object)}, but allows to provide options for the handler runner. This allows
     * to configure for the Java API the executor where to run the handler code, or the Kotlin API
     * the coroutine context.
     *
     * <p>Look at the respective documentations of the HandlerRunner class in the Java or in the
     * Kotlin module.
     *
     * <p>If you want to modify some of the service definition options, such as documentation,
     * inactivity timeout, and so on, use {@link #bind(Object, HandlerRunner.Options, Consumer)}
     * instead.
     *
     * @see #bind(Object)
     */
    public Builder bind(Object service, HandlerRunner.Options options) {
      return this.bind(service, options, ignored -> {});
    }

    /**
     * Same as {@link #bind(Object)} but allows to configure the {@link ServiceDefinition} before
     * binding it.
     *
     * <pre>{@code
     * Endpoint endpoint = Endpoint
     *   .builder()
     *   .bind(
     *     new Counter(),
     *     // Configure the service
     *     s -> s.journalRetention(Duration.ofDays(1))
     *   )
     *   .build();
     * }</pre>
     *
     * @see #bind(Object)
     * @see ServiceDefinition.Configurator
     */
    public Builder bind(Object service, Consumer<ServiceDefinition.Configurator> configurator) {
      return this.bind(
          ServiceDefinitionFactories.discover(service)
              .create(service, null)
              .configure(configurator));
    }

    /**
     * Same as {@link #bind(Object, HandlerRunner.Options)} but allows to configure the {@link
     * ServiceDefinition} before binding it.
     *
     * @see #bind(Object, HandlerRunner.Options)
     * @see ServiceDefinition.Configurator
     */
    public Builder bind(
        Object service,
        HandlerRunner.Options options,
        Consumer<ServiceDefinition.Configurator> configurator) {
      return this.bind(
          ServiceDefinitionFactories.discover(service)
              .create(service, options)
              .configure(configurator));
    }

    /** Add a manual {@link ServiceDefinition} to the endpoint. */
    public Builder bind(ServiceDefinition serviceDefinition) {
      this.services.add(serviceDefinition);
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
              .collect(Collectors.toMap(ServiceDefinition::getServiceName, Function.identity())),
          this.openTelemetry,
          this.requestIdentityVerifier,
          this.experimentalContextEnabled);
    }
  }

  // --- Factory methods

  public static Builder builder() {
    return new Builder();
  }

  /**
   * @see Builder#bind(Object)
   */
  public static Builder bind(Object object) {
    return new Builder().bind(object);
  }

  /**
   * @see Builder#bind(Object, HandlerRunner.Options)
   */
  public static Builder bind(Object service, HandlerRunner.Options options) {
    return new Builder().bind(service, options);
  }

  /**
   * @see Builder#bind(Object, Consumer)
   */
  public static Builder bind(Object object, Consumer<ServiceDefinition.Configurator> configurator) {
    return new Builder().bind(object, configurator);
  }

  /**
   * @see Builder#bind(Object, HandlerRunner.Options, Consumer)
   */
  public static Builder bind(
      Object service,
      HandlerRunner.Options options,
      Consumer<ServiceDefinition.Configurator> configurator) {
    return new Builder().bind(service, options, configurator);
  }

  /**
   * @see Builder#bind(ServiceDefinition)
   */
  public static Builder bind(ServiceDefinition serviceDefinition) {
    return new Builder().bind(serviceDefinition);
  }

  // --- Implementation

  public ServiceDefinition resolveService(String serviceName) {
    return services.get(serviceName);
  }

  public Stream<ServiceDefinition> getServiceDefinitions() {
    return this.services.values().stream();
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
