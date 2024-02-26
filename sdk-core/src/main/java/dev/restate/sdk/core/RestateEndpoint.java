// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.ComponentAdapter;
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

  private final Map<String, ComponentDefinition> components;
  private final Tracer tracer;
  private final DeploymentManifest deploymentManifest;

  private RestateEndpoint(
      DeploymentManifestSchema.ProtocolMode protocolMode,
      Map<String, ComponentDefinition> components,
      Tracer tracer) {
    this.components = components;
    this.tracer = tracer;
    this.deploymentManifest = new DeploymentManifest(protocolMode, components);

    this.logCreation();
  }

  public ResolvedEndpointHandler resolve(
      String componentName,
      String handlerName,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor,
      @Nullable Executor userCodeExecutor)
      throws ProtocolException {
    // Resolve the service method definition
    ComponentDefinition svc = this.components.get(componentName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(componentName, handlerName);
    }
    String fullyQualifiedServiceMethod = componentName + "/" + handlerName;
    HandlerDefinition handler = svc.getHandler(handlerName);
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
    SyscallsInternal syscalls =
        syscallExecutor != null
            ? new ExecutorSwitchingSyscalls(new SyscallsImpl(stateMachine), syscallExecutor)
            : new SyscallsImpl(stateMachine);

    return new ResolvedEndpointHandlerImpl(
        stateMachine, loggingContextSetter, syscalls, handler.getHandler(), userCodeExecutor);
  }

  public DeploymentManifestSchema handleDiscoveryRequest() {
    DeploymentManifestSchema response = this.deploymentManifest.manifest();
    LOG.info(
        "Replying to service discovery request with services [{}]",
        response.getComponents().stream()
            .map(Component::getFullyQualifiedComponentName)
            .collect(Collectors.joining(",")));
    return response;
  }

  private void logCreation() {
    LOG.info("Registered services: {}", this.components.keySet());
  }

  // -- Builder

  public static Builder newBuilder(DeploymentManifestSchema.ProtocolMode protocolMode) {
    return new Builder(protocolMode);
  }

  public static class Builder {

    private final List<ComponentDefinition> components = new ArrayList<>();
    private final DeploymentManifestSchema.ProtocolMode protocolMode;
    private Tracer tracer = OpenTelemetry.noop().getTracer("NOOP");

    public Builder(DeploymentManifestSchema.ProtocolMode protocolMode) {
      this.protocolMode = protocolMode;
    }

    public Builder with(ComponentDefinition component) {
      this.components.add(component);
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
                      ComponentDefinition::getFullyQualifiedServiceName, Function.identity())),
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
    String SERVICE_METHOD_KEY = "restateServiceMethod";
    String SERVICE_INVOCATION_STATUS_KEY = "restateInvocationStatus";

    LoggingContextSetter THREAD_LOCAL_INSTANCE =
        new LoggingContextSetter() {
          @Override
          public void setServiceMethod(String serviceMethod) {
            ThreadContext.put(SERVICE_METHOD_KEY, serviceMethod);
          }

          @Override
          public void setInvocationId(String id) {
            ThreadContext.put(INVOCATION_ID_KEY, id);
          }

          @Override
          public void setInvocationStatus(String invocationStatus) {
            ThreadContext.put(SERVICE_INVOCATION_STATUS_KEY, invocationStatus);
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

    private final List<ComponentAdapter> adapters;

    private ComponentAdapterDiscovery() {
      this.adapters =
          ServiceLoader.load(ComponentAdapter.class).stream()
              .map(ServiceLoader.Provider::get)
              .collect(Collectors.toList());
    }

    private @Nullable ComponentAdapter discoverAdapter(Object service) {
      return this.adapters.stream()
          .filter(sa -> sa.supportsObject(service))
          .findFirst()
          .orElse(null);
    }
  }

  /** Resolve the code generated {@link ComponentAdapter} */
  @SuppressWarnings("unchecked")
  public static ComponentAdapter<Object> discoverAdapter(Object component) {
    return Objects.requireNonNull(
        ComponentAdapterSingleton.INSTANCE.discoverAdapter(component),
        () ->
            "ComponentAdapter class not found for service "
                + component.getClass().getCanonicalName()
                + ". "
                + "Make sure the annotation processor is correctly configured to generate the ComponentAdapter, "
                + "and it generates the META-INF/services/"
                + ComponentAdapter.class.getCanonicalName()
                + " file containing the generated class. "
                + "If you're using fat jars, make sure the jar plugin correctly squashes all the META-INF/services files. "
                + "Found ComponentAdapter: "
                + ComponentAdapterSingleton.INSTANCE.adapters);
  }
}
