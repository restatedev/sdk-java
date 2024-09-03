// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.auth.RequestIdentityVerifier;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.common.syscalls.ServiceDefinitionFactory;
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
  private final boolean experimentalContextEnabled;

  private RestateEndpoint(
      EndpointManifestSchema.ProtocolMode protocolMode,
      Map<String, ServiceAndOptions<?>> services,
      Tracer tracer,
      RequestIdentityVerifier requestIdentityVerifier,
      boolean experimentalContextEnabled) {
    this.services = services;
    this.tracer = tracer;
    this.requestIdentityVerifier = requestIdentityVerifier;
    this.deploymentManifest =
        new EndpointManifest(
            protocolMode,
            services.values().stream().map(c -> c.service),
            experimentalContextEnabled);
    this.experimentalContextEnabled = experimentalContextEnabled;

    LOG.info("Registered services: {}", this.services.keySet());
  }

  public ResolvedEndpointHandler resolve(
      String contentType,
      String componentName,
      String handlerName,
      RequestIdentityVerifier.Headers headers,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor)
      throws ProtocolException {
    final Protocol.ServiceProtocolVersion serviceProtocolVersion =
        ServiceProtocol.parseServiceProtocolVersion(contentType);

    if (!ServiceProtocol.isSupported(serviceProtocolVersion, this.experimentalContextEnabled)) {
      throw new ProtocolException(
          String.format(
              "Service endpoint does not support the service protocol version '%s'.", contentType),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

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
            componentName,
            fullyQualifiedServiceMethod,
            span,
            loggingContextSetter,
            serviceProtocolVersion);

    return new ResolvedEndpointHandlerImpl(
        serviceProtocolVersion, stateMachine, handler, svc.options, syscallExecutor);
  }

  public DiscoveryResponse handleDiscoveryRequest(String acceptContentType)
      throws ProtocolException {
    Discovery.ServiceDiscoveryProtocolVersion version =
        ServiceProtocol.selectSupportedServiceDiscoveryProtocolVersion(acceptContentType);
    if (!ServiceProtocol.isSupported(version)) {
      throw new ProtocolException(
          String.format(
              "Unsupported Discovery version in the Accept header '%s'", acceptContentType),
          ProtocolException.UNSUPPORTED_MEDIA_TYPE_CODE);
    }

    EndpointManifestSchema response = this.deploymentManifest.manifest();
    LOG.info(
        "Replying to discovery request with services [{}]",
        response.getServices().stream().map(Service::getName).collect(Collectors.joining(",")));

    return new DiscoveryResponse(
        ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue(version),
        ServiceProtocol.serializeManifest(version, response));
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
    private boolean experimentalContextEnabled = false;

    public Builder(EndpointManifestSchema.ProtocolMode protocolMode) {
      this.protocolMode = protocolMode;
    }

    public <O> Builder bind(ServiceDefinition<O> component, @Nullable O options) {
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

    public Builder enablePreviewContext() {
      this.experimentalContextEnabled = true;
      return this;
    }

    public RestateEndpoint build() {
      return new RestateEndpoint(
          this.protocolMode,
          this.services.stream()
              .collect(Collectors.toMap(c -> c.service.getServiceName(), Function.identity())),
          tracer,
          requestIdentityVerifier,
          experimentalContextEnabled);
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

  private static class ServiceDefinitionFactorySingleton {
    private static final ServiceDefinitionFactoryDiscovery INSTANCE =
        new ServiceDefinitionFactoryDiscovery();
  }

  @SuppressWarnings("rawtypes")
  private static class ServiceDefinitionFactoryDiscovery {

    private final List<ServiceDefinitionFactory> factories;

    private ServiceDefinitionFactoryDiscovery() {
      this.factories =
          ServiceLoader.load(ServiceDefinitionFactory.class).stream()
              .map(ServiceLoader.Provider::get)
              .collect(Collectors.toList());
    }

    private @Nullable ServiceDefinitionFactory discoverFactory(Object service) {
      return this.factories.stream().filter(sa -> sa.supports(service)).findFirst().orElse(null);
    }
  }

  /** Resolve the code generated {@link ServiceDefinitionFactory} */
  @SuppressWarnings("unchecked")
  public static ServiceDefinitionFactory<Object, Object> discoverServiceDefinitionFactory(
      Object service) {
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

  private static class ServiceAndOptions<O> {
    private final ServiceDefinition<O> service;
    private final O options;

    ServiceAndOptions(ServiceDefinition<O> service, O options) {
      this.service = service;
      this.options = options;
    }
  }

  public static class DiscoveryResponse {
    private final String contentType;
    private final byte[] serializedManifest;

    private DiscoveryResponse(String contentType, byte[] serializedManifest) {
      this.contentType = contentType;
      this.serializedManifest = serializedManifest;
    }

    public String getContentType() {
      return contentType;
    }

    public byte[] getSerializedManifest() {
      return serializedManifest;
    }
  }
}
