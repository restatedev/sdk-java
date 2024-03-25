// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.BindableComponentFactory;
import dev.restate.sdk.common.syscalls.ComponentDefinition;
import dev.restate.sdk.common.syscalls.HandlerDefinition;
import dev.restate.sdk.core.manifest.Component;
import dev.restate.sdk.core.manifest.DeploymentManifestSchema;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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

  private final Map<String, ComponentAndOptions<?>> components;
  private final Tracer tracer;
  private final DeploymentManifest deploymentManifest;

  private RestateEndpoint(
      DeploymentManifestSchema.ProtocolMode protocolMode,
      Map<String, ComponentAndOptions<?>> components,
      Tracer tracer) {
    this.components = components;
    this.tracer = tracer;
    this.deploymentManifest =
        new DeploymentManifest(protocolMode, components.values().stream().map(c -> c.component));

    this.logCreation();
  }

  public ResolvedEndpointHandler resolve(
      String componentName,
      String handlerName,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor)
      throws ProtocolException {
    // Resolve the service method definition
    @SuppressWarnings("unchecked")
    ComponentAndOptions<Object> svc =
        (ComponentAndOptions<Object>) this.components.get(componentName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(componentName, handlerName);
    }
    String fullyQualifiedServiceMethod = componentName + "/" + handlerName;
    HandlerDefinition<Object> handler = svc.component.getHandler(handlerName);
    if (handler == null) {
      throw ProtocolException.methodNotFound(componentName, handlerName);
    }

    // Generate the span
    Span span =
        tracer
            .spanBuilder("Invoke method")
            .setSpanKind(SpanKind.SERVER)
            .setParent(otelContext)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "restate")
            .setAttribute(SemanticAttributes.RPC_SERVICE, componentName)
            .setAttribute(SemanticAttributes.RPC_METHOD, handlerName)
            .startSpan();

    // Setup logging context
    loggingContextSetter.setServiceMethod(fullyQualifiedServiceMethod);

    // Instantiate state machine, syscall and grpc bridge
    InvocationStateMachine stateMachine =
        new InvocationStateMachine(
            componentName,
            fullyQualifiedServiceMethod,
            span,
            s -> loggingContextSetter.setInvocationStatus(s.toString()));

    return new ResolvedEndpointHandlerImpl(
        stateMachine, loggingContextSetter, handler.getHandler(), svc.options, syscallExecutor);
  }

  public DeploymentManifestSchema handleDiscoveryRequest() {
    DeploymentManifestSchema response = this.deploymentManifest.manifest();
    LOG.info(
        "Replying to discovery request with components [{}]",
        response.getComponents().stream()
            .map(Component::getFullyQualifiedComponentName)
            .collect(Collectors.joining(",")));
    return response;
  }

  private void logCreation() {
    LOG.info("Registered components: {}", this.components.keySet());
  }

  // -- Builder

  public static Builder newBuilder(DeploymentManifestSchema.ProtocolMode protocolMode) {
    return new Builder(protocolMode);
  }

  public static class Builder {

    private final List<ComponentAndOptions<?>> components = new ArrayList<>();
    private final DeploymentManifestSchema.ProtocolMode protocolMode;
    private Tracer tracer = OpenTelemetry.noop().getTracer("NOOP");

    public Builder(DeploymentManifestSchema.ProtocolMode protocolMode) {
      this.protocolMode = protocolMode;
    }

    public <O> Builder bind(ComponentDefinition<O> component, O options) {
      this.components.add(new ComponentAndOptions<>(component, options));
      return this;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public RestateEndpoint build() {
      return new RestateEndpoint(
          this.protocolMode,
          this.components.stream()
              .collect(
                  Collectors.toMap(
                      c -> c.component.getFullyQualifiedComponentName(), Function.identity())),
          tracer);
    }
  }

  /**
   * Interface to abstract setting the logging context variables.
   *
   * <p>In classic multithreaded environments, you can just use {@link
   * LoggingContextSetter#THREAD_LOCAL_INSTANCE}, though the caller of {@link RestateEndpoint} must
   * take care of the cleanup of the thread local map.
   */
  public interface LoggingContextSetter {

    String INVOCATION_ID_KEY = "restateInvocationId";
    String COMPONENT_HANDLER_KEY = "restateComponentHandler";
    String INVOCATION_STATUS_KEY = "restateInvocationStatus";

    LoggingContextSetter THREAD_LOCAL_INSTANCE =
        new LoggingContextSetter() {
          @Override
          public void setServiceMethod(String serviceMethod) {
            ThreadContext.put(COMPONENT_HANDLER_KEY, serviceMethod);
          }

          @Override
          public void setInvocationId(String id) {
            ThreadContext.put(INVOCATION_ID_KEY, id);
          }

          @Override
          public void setInvocationStatus(String invocationStatus) {
            ThreadContext.put(INVOCATION_STATUS_KEY, invocationStatus);
          }
        };

    void setServiceMethod(String serviceMethod);

    void setInvocationId(String id);

    void setInvocationStatus(String invocationStatus);
  }

  private static class ComponentAdapterSingleton {
    private static final ComponentAdapterDiscovery INSTANCE = new ComponentAdapterDiscovery();
  }

  @SuppressWarnings("rawtypes")
  private static class ComponentAdapterDiscovery {

    private final List<BindableComponentFactory> adapters;

    private ComponentAdapterDiscovery() {
      this.adapters =
          ServiceLoader.load(BindableComponentFactory.class).stream()
              .map(ServiceLoader.Provider::get)
              .collect(Collectors.toList());
    }

    private @Nullable BindableComponentFactory discoverAdapter(Object service) {
      return this.adapters.stream().filter(sa -> sa.supports(service)).findFirst().orElse(null);
    }
  }

  /** Resolve the code generated {@link BindableComponentFactory} */
  @SuppressWarnings("unchecked")
  public static BindableComponentFactory<Object, Object> discoverBindableComponentFactory(
      Object component) {
    return Objects.requireNonNull(
        ComponentAdapterSingleton.INSTANCE.discoverAdapter(component),
        () ->
            "ComponentAdapter class not found for service "
                + component.getClass().getCanonicalName()
                + ". "
                + "Make sure the annotation processor is correctly configured to generate the ComponentAdapter, "
                + "and it generates the META-INF/services/"
                + BindableComponentFactory.class.getCanonicalName()
                + " file containing the generated class. "
                + "If you're using fat jars, make sure the jar plugin correctly squashes all the META-INF/services files. "
                + "Found ComponentAdapter: "
                + ComponentAdapterSingleton.INSTANCE.adapters);
  }

  private static class ComponentAndOptions<O> {
    private final ComponentDefinition<O> component;
    private final O options;

    ComponentAndOptions(ComponentDefinition<O> component, O options) {
      this.component = component;
      this.options = options;
    }
  }
}
