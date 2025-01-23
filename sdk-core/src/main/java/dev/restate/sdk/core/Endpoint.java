// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.definition.ServiceDefinition;
import dev.restate.sdk.definition.ServiceDefinitionFactory;
import io.opentelemetry.api.OpenTelemetry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** Restate endpoint, encapsulating the configured services, together with additional options. */
public final class Endpoint {

  private static final Logger LOG = LogManager.getLogger(Endpoint.class);

  private final Map<String, ServiceAndOptions<?>> services;
  private final OpenTelemetry openTelemetry;
  private final RequestIdentityVerifier requestIdentityVerifier;
  private final boolean experimentalContextEnabled;

  private Endpoint(
      Map<String, ServiceAndOptions<?>> services,
      OpenTelemetry openTelemetry,
      RequestIdentityVerifier requestIdentityVerifier,
      boolean experimentalContextEnabled) {
    this.services = services;
    this.openTelemetry = openTelemetry;
    this.requestIdentityVerifier = requestIdentityVerifier;
    this.experimentalContextEnabled = experimentalContextEnabled;
  }

  public static class Builder {
    private final List<ServiceAndOptions<?>> services = new ArrayList<>();
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
      return this.bind(discoverServiceDefinitionFactory(service).create(service));
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
      this.services.add(new ServiceAndOptions<>(serviceDefinition, options));
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
              .collect(Collectors.toMap(c -> c.service.getServiceName(), Function.identity())),
          this.openTelemetry,
          this.requestIdentityVerifier,
          this.experimentalContextEnabled);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  record ServiceAndOptions<O>(ServiceDefinition<O> service, O options) {}

  private static class ServiceDefinitionFactorySingleton {
    private static final ServiceDefinitionFactoryDiscovery INSTANCE =
        new ServiceDefinitionFactoryDiscovery();
  }

  @SuppressWarnings("rawtypes")
  private static class ServiceDefinitionFactoryDiscovery {

    private final List<ServiceDefinitionFactory> factories;

    private ServiceDefinitionFactoryDiscovery() {
      this.factories = new ArrayList<>();

      var serviceLoaderIterator = ServiceLoader.load(ServiceDefinitionFactory.class).iterator();
      while (serviceLoaderIterator.hasNext()) {
        try {
          this.factories.add(serviceLoaderIterator.next());
        } catch (ServiceConfigurationError | Exception e) {
          LOG.debug(
              "Found service that cannot be loaded using service provider. "
                  + "You can ignore this message during development.\n"
                  + "This might be the result of using a compiler with incremental builds (e.g. IntelliJ IDEA) "
                  + "that updated a dirty META-INF file after removing/renaming an annotated service.",
              e);
        }
      }
    }

    private @Nullable ServiceDefinitionFactory discoverFactory(Object service) {
      return this.factories.stream().filter(sa -> sa.supports(service)).findFirst().orElse(null);
    }
  }

  /** Resolve the code generated {@link ServiceDefinitionFactory} */
  @SuppressWarnings("unchecked")
  public static ServiceDefinitionFactory<Object, Object> discoverServiceDefinitionFactory(
      Object service) {
    if (service instanceof ServiceDefinitionFactory<?, ?>) {
      // We got this already
      return (ServiceDefinitionFactory<Object, Object>) service;
    }
    if (service instanceof ServiceDefinition<?>) {
      // We got this already
      return new ServiceDefinitionFactory<>() {
        @Override
        public ServiceDefinition<Object> create(Object serviceObject) {
          return (ServiceDefinition<Object>) serviceObject;
        }

        @Override
        public boolean supports(Object serviceObject) {
          return serviceObject == service;
        }
      };
    }
    return Objects.requireNonNull(
        ServiceDefinitionFactorySingleton.INSTANCE.discoverFactory(service),
        () ->
            "ServiceDefinitionFactory class not found for service "
                + service.getClass().getCanonicalName()
                + ". "
                + "Make sure the annotation processor is correctly configured to generate the ServiceDefinitionFactory, "
                + "and it generates the META-INF/services/"
                + ServiceDefinitionFactory.class.getCanonicalName()
                + " file containing the generated class. "
                + "If you're using fat jars, make sure the jar plugin correctly squashes all the META-INF/services files. "
                + "Found ServiceAdapter: "
                + ServiceDefinitionFactorySingleton.INSTANCE.factories);
  }

  Map<String, ServiceAndOptions<?>> getServices() {
    return services;
  }

  OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  RequestIdentityVerifier getRequestIdentityVerifier() {
    return requestIdentityVerifier;
  }

  boolean isExperimentalContextEnabled() {
    return experimentalContextEnabled;
  }
}
