// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.auth.RequestIdentityVerifier;
import dev.restate.sdk.common.BindableServiceFactory;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.core.manifest.EndpointManifestSchema;
import dev.restate.sdk.core.manifest.Service;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.jspecify.annotations.Nullable;

public class RestateEndpoint {

  private static final Logger LOG = LogManager.getLogger(RestateEndpoint.class);

  private final Map<String, ServiceAndOptions<?>> services;
  private final Tracer tracer;
  private final RequestIdentityVerifier requestIdentityVerifier;
  private final EndpointManifest deploymentManifest;

  private RestateEndpoint(
      EndpointManifestSchema.ProtocolMode protocolMode,
      Map<String, ServiceAndOptions<?>> services,
      Tracer tracer,
      RequestIdentityVerifier requestIdentityVerifier) {
    this.services = services;
    this.tracer = tracer;
    this.requestIdentityVerifier = requestIdentityVerifier;
    this.deploymentManifest =
        new EndpointManifest(protocolMode, services.values().stream().map(c -> c.service));

    this.logCreation();
  }

  public ResolvedEndpointHandler resolve(
      String componentName,
      String handlerName,
      RequestIdentityVerifier.Headers headers,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor)
      throws ProtocolException {
    // Resolve the service method definition
    @SuppressWarnings("unchecked")
    ServiceAndOptions<Object> svc = (ServiceAndOptions<Object>) this.services.get(componentName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(componentName, handlerName);
    }
    String fullyQualifiedServiceMethod = componentName + "/" + handlerName;
    HandlerDefinition<?, ?, Object> handler = svc.service.getHandler(handlerName);
    if (handler == null) {
      throw ProtocolException.methodNotFound(componentName, handlerName);
    }

    // Verify request
    if (requestIdentityVerifier != null) {
      try {
        requestIdentityVerifier.verifyRequest(headers);
      } catch (Exception e) {
        throw ProtocolException.unauthorized(e);
      }
    }

    // Generate the span
    Span span =
        tracer
            .spanBuilder("Invoke method")
            .setSpanKind(SpanKind.SERVER)
            .setParent(otelContext)
            .startSpan();

    // Setup logging context
    loggingContextSetter.set(
        LoggingContextSetter.INVOCATION_TARGET_KEY, fullyQualifiedServiceMethod);

    // Instantiate state machine, syscall and grpc bridge
    InvocationStateMachine stateMachine =
        new InvocationStateMachine(
            componentName, fullyQualifiedServiceMethod, span, loggingContextSetter);

    return new ResolvedEndpointHandlerImpl(stateMachine, handler, svc.options, syscallExecutor);
  }

  public EndpointManifestSchema handleDiscoveryRequest() {
    EndpointManifestSchema response = this.deploymentManifest.manifest();
    LOG.info(
        "Replying to discovery request with services [{}]",
        response.getServices().stream().map(Service::getName).collect(Collectors.joining(",")));
    return response;
  }

  private void logCreation() {
    LOG.info("Registered services: {}", this.services.keySet());
  }

  // -- Builder

  public static Builder newBuilder(EndpointManifestSchema.ProtocolMode protocolMode) {
    return new Builder(protocolMode);
  }

  public static class Builder {

    private final List<ServiceAndOptions<?>> services = new ArrayList<>();
    private final EndpointManifestSchema.ProtocolMode protocolMode;
    private RequestIdentityVerifier requestIdentityVerifier;
    private Tracer tracer = OpenTelemetry.noop().getTracer("NOOP");

    public Builder(EndpointManifestSchema.ProtocolMode protocolMode) {
      this.protocolMode = protocolMode;
    }

    public <O> Builder bind(ServiceDefinition<O> component, O options) {
      this.services.add(new ServiceAndOptions<>(component, options));
      return this;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public Builder withRequestIdentityVerifier(RequestIdentityVerifier requestIdentityVerifier) {
      this.requestIdentityVerifier = requestIdentityVerifier;
      return this;
    }

    public RestateEndpoint build() {
      return new RestateEndpoint(
          this.protocolMode,
          this.services.stream()
              .collect(Collectors.toMap(c -> c.service.getServiceName(), Function.identity())),
          tracer,
          requestIdentityVerifier);
    }
  }

  /**
   * Interface to abstract setting the logging context variables.
   *
   * <p>In classic multithreaded environments, you can just use {@link
   * LoggingContextSetter#THREAD_LOCAL_INSTANCE}, though the caller of {@link RestateEndpoint} must
   * take care of the cleanup of the thread local map.
   */
  @FunctionalInterface
  public interface LoggingContextSetter {

    String INVOCATION_ID_KEY = "restateInvocationId";
    String INVOCATION_TARGET_KEY = "restateInvocationTarget";
    String INVOCATION_STATUS_KEY = "restateInvocationStatus";

    LoggingContextSetter THREAD_LOCAL_INSTANCE = ThreadContext::put;

    void set(String key, String value);
  }

  private static class ServiceAdapterSingleton {
    private static final ServiceAdapterDiscovery INSTANCE = new ServiceAdapterDiscovery();
  }

  @SuppressWarnings("rawtypes")
  private static class ServiceAdapterDiscovery {

    private final List<BindableServiceFactory> adapters;

    private ServiceAdapterDiscovery() {
      this.adapters =
          ServiceLoader.load(BindableServiceFactory.class).stream()
              .map(ServiceLoader.Provider::get)
              .collect(Collectors.toList());
    }

    private @Nullable BindableServiceFactory discoverAdapter(Object service) {
      return this.adapters.stream().filter(sa -> sa.supports(service)).findFirst().orElse(null);
    }
  }

  /** Resolve the code generated {@link BindableServiceFactory} */
  @SuppressWarnings("unchecked")
  public static BindableServiceFactory<Object, Object> discoverBindableServiceFactory(
      Object service) {
    return Objects.requireNonNull(
        ServiceAdapterSingleton.INSTANCE.discoverAdapter(service),
        () ->
            "ServiceAdapter class not found for service "
                + service.getClass().getCanonicalName()
                + ". "
                + "Make sure the annotation processor is correctly configured to generate the ServiceAdapter, "
                + "and it generates the META-INF/services/"
                + BindableServiceFactory.class.getCanonicalName()
                + " file containing the generated class. "
                + "If you're using fat jars, make sure the jar plugin correctly squashes all the META-INF/services files. "
                + "Found ServiceAdapter: "
                + ServiceAdapterSingleton.INSTANCE.adapters);
  }

  private static class ServiceAndOptions<O> {
    private final ServiceDefinition<O> service;
    private final O options;

    ServiceAndOptions(ServiceDefinition<O> service, O options) {
      this.service = service;
      this.options = options;
    }
  }
}
