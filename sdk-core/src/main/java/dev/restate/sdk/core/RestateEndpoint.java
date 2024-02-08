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
import dev.restate.sdk.common.ServiceAdapter;
import dev.restate.sdk.common.ServicesBundle;
import io.grpc.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class RestateEndpoint {

  private static final Logger LOG = LogManager.getLogger(RestateEndpoint.class);

  private final Map<String, ServerServiceDefinition> services;
  private final Tracer tracer;
  private final ServiceDiscoveryHandler serviceDiscoveryHandler;

  private RestateEndpoint(
      Discovery.ProtocolMode protocolMode,
      Map<String, ServerServiceDefinition> services,
      Tracer tracer) {
    this.services = services;
    this.tracer = tracer;
    this.serviceDiscoveryHandler = new ServiceDiscoveryHandler(protocolMode, services);

    this.logCreation();
  }

  @SuppressWarnings("unchecked")
  public InvocationHandler resolve(
      String serviceName,
      String methodName,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor,
      @Nullable Executor userCodeExecutor)
      throws ProtocolException {
    // Resolve the service method definition
    ServerServiceDefinition svc = this.services.get(serviceName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(serviceName, methodName);
    }
    String fullyQualifiedServiceMethod = serviceName + "/" + methodName;
    ServerMethodDefinition<?, ?> method = svc.getMethod(fullyQualifiedServiceMethod);
    if (method == null) {
      throw ProtocolException.methodNotFound(serviceName, methodName);
    }

    // Generate the span
    Span span =
        tracer
            .spanBuilder("Invoke method")
            .setSpanKind(SpanKind.SERVER)
            .setParent(otelContext)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "restate")
            .setAttribute(SemanticAttributes.RPC_SERVICE, serviceName)
            .setAttribute(SemanticAttributes.RPC_METHOD, methodName)
            .startSpan();

    // Setup logging context
    loggingContextSetter.setServiceMethod(fullyQualifiedServiceMethod);

    // Instantiate state machine, syscall and grpc bridge
    InvocationStateMachine stateMachine =
        new InvocationStateMachine(
            serviceName,
            fullyQualifiedServiceMethod,
            span,
            s -> loggingContextSetter.setInvocationStatus(s.toString()));
    SyscallsInternal syscalls =
        syscallExecutor != null
            ? new ExecutorSwitchingSyscalls(new SyscallsImpl(stateMachine), syscallExecutor)
            : new SyscallsImpl(stateMachine);

    return new InvocationHandler() {

      @Override
      public InvocationFlow.InvocationInputSubscriber input() {
        return new ExceptionCatchingInvocationInputSubscriber(stateMachine);
      }

      @Override
      public InvocationFlow.InvocationOutputPublisher output() {
        return stateMachine;
      }

      @Override
      public void start() {
        LOG.info("Start processing invocation");
        stateMachine.start(
            invocationId -> {
              // Set invocation id in logging context
              loggingContextSetter.setInvocationId(invocationId.toString());

              // Prepare RpcHandler
              RpcHandler m = new GrpcUnaryRpcHandler(method, syscalls, userCodeExecutor);

              // Wire up "close" notification
              stateMachine.registerCloseCallback(c -> m.notifyClosed());

              // Start RpcHandler
              m.start();
            });
      }
    };
  }

  public Discovery.ServiceDiscoveryResponse handleDiscoveryRequest(
      Discovery.ServiceDiscoveryRequest request) {
    Discovery.ServiceDiscoveryResponse response = this.serviceDiscoveryHandler.handle(request);
    LOG.info(
        "Replying to service discovery request with services [{}]",
        String.join(",", response.getServicesList()));
    return response;
  }

  private void logCreation() {
    LOG.info("Registered services: {}", this.services.keySet());
  }

  // -- Builder

  public static Builder newBuilder(Discovery.ProtocolMode protocolMode) {
    return new Builder(protocolMode);
  }

  public static class Builder {

    private final List<ServerServiceDefinition> services = new ArrayList<>();
    private final Discovery.ProtocolMode protocolMode;
    private Tracer tracer = OpenTelemetry.noop().getTracer("NOOP");

    public Builder(Discovery.ProtocolMode protocolMode) {
      this.protocolMode = protocolMode;
    }

    public Builder withService(BindableService service) {
      this.services.add(service.bindService());
      return this;
    }

    public Builder withService(ServerServiceDefinition service) {
      this.services.add(service);
      return this;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public RestateEndpoint build() {
      return new RestateEndpoint(
          this.protocolMode,
          this.services.stream()
              .collect(
                  Collectors.toMap(
                      svc -> svc.getServiceDescriptor().getName(), Function.identity())),
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

  /** Resolve the code generated {@link ServiceAdapter} */
  public static ServicesBundle adapt(Object entity) {
    Class<?> userClazz = entity.getClass();

    // Find Service code-generated class
    // TODO This could be done with an SPI
    Class<?> serviceAdapterClazz;
    try {
      serviceAdapterClazz = Class.forName(userClazz.getCanonicalName() + "ServiceAdapter");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          "Code generated class not found. "
              + "Make sure the annotation processor is correctly configured.",
          e);
    }

    // Instantiate it
    ServiceAdapter<Object> serviceAdapter;
    try {
      //noinspection unchecked
      serviceAdapter = (ServiceAdapter<Object>) serviceAdapterClazz.getConstructor().newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException(
          "Cannot invoke code generated class constructor. "
              + "Make sure the annotation processor is correctly configured.",
          e);
    }

    return serviceAdapter.adapt(entity);
  }
}
