package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.sdk.core.InvocationId;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.syscalls.Syscalls;
import io.grpc.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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

public class RestateGrpcServer {

  private static final Logger LOG = LogManager.getLogger(RestateGrpcServer.class);
  static final Context.Key<String> SERVICE_METHOD =
      Context.key("restate.dev/logging_service_method");

  private final Map<String, ServerServiceDefinition> services;
  private final Serde serde;
  private final Tracer tracer;
  private final ServiceDiscoveryHandler serviceDiscoveryHandler;

  private RestateGrpcServer(
      Discovery.ProtocolMode protocolMode,
      Map<String, ServerServiceDefinition> services,
      Serde serde,
      Tracer tracer) {
    this.services = services;
    this.serde = serde;
    this.tracer = tracer;
    this.serviceDiscoveryHandler = new ServiceDiscoveryHandler(protocolMode, services);
  }

  @SuppressWarnings("unchecked")
  public InvocationHandler resolve(
      String serviceName,
      String methodName,
      io.opentelemetry.context.Context otelContext,
      LoggingContextSetter loggingContextSetter,
      @Nullable Executor syscallExecutor,
      @Nullable Executor serverCallListenerExecutor)
      throws ProtocolException {
    // Resolve the service method definition
    ServerServiceDefinition svc = this.services.get(serviceName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(serviceName, methodName);
    }
    String serviceMethodName = serviceName + "/" + methodName;
    ServerMethodDefinition<MessageLite, MessageLite> method =
        (ServerMethodDefinition<MessageLite, MessageLite>) svc.getMethod(serviceMethodName);
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
    loggingContextSetter.setServiceMethod(serviceMethodName);

    // Instantiate state machine, syscall and grpc bridge
    InvocationStateMachine stateMachine = new InvocationStateMachine(serviceName, span);
    SyscallsInternal syscalls =
        syscallExecutor != null
            ? ExecutorSwitchingWrappers.syscalls(
                new SyscallsImpl(stateMachine, this.serde), syscallExecutor)
            // We still wrap with syscalls executor switching to exploit the error handling
            : ExecutorSwitchingWrappers.syscalls(
                new SyscallsImpl(stateMachine, this.serde), Runnable::run);
    RestateServerCall bridge = new RestateServerCall(method.getMethodDescriptor(), syscalls);

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
        LOG.debug("Start processing call to {}/{}", serviceName, methodName);
        stateMachine.start(
            invocationId -> {
              // Set invocation id in logging context
              loggingContextSetter.setInvocationId(invocationId.toString());

              // Create the listener and create the decorators chain
              ServerCall.Listener<MessageLite> grpcListener =
                  Contexts.interceptCall(
                      Context.current()
                          .withValue(SERVICE_METHOD, serviceMethodName)
                          .withValue(InvocationId.INVOCATION_ID_KEY, invocationId)
                          .withValue(Syscalls.SYSCALLS_KEY, syscalls),
                      bridge,
                      new Metadata(),
                      method.getServerCallHandler());
              RestateServerCallListener<MessageLite> restateListener =
                  new GrpcServerCallListenerAdaptor<>(grpcListener, bridge);
              if (serverCallListenerExecutor != null) {
                restateListener =
                    ExecutorSwitchingWrappers.serverCallListener(
                        restateListener, serverCallListenerExecutor);
              }

              bridge.setListener(restateListener);
            });
      }
    };
  }

  public Discovery.ServiceDiscoveryResponse handleDiscoveryRequest(
      Discovery.ServiceDiscoveryRequest request) {
    return this.serviceDiscoveryHandler.handle(request);
  }

  // -- Builder

  public static Builder newBuilder(Discovery.ProtocolMode protocolMode) {
    return new Builder(protocolMode);
  }

  public static class Builder {

    private final List<ServerServiceDefinition> services = new ArrayList<>();
    private final Discovery.ProtocolMode protocolMode;
    private Serde serde;
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

    public Builder withSerde(Serde... serde) {
      this.serde = Serdes.chain(serde);
      return this;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public RestateGrpcServer build() {
      if (serde == null) {
        serde = Serdes.getInstance().getDiscoveredSerde();
      }

      return new RestateGrpcServer(
          this.protocolMode,
          this.services.stream()
              .collect(
                  Collectors.toMap(
                      svc -> svc.getServiceDescriptor().getName(), Function.identity())),
          serde,
          tracer);
    }
  }

  /**
   * Interface to abstract setting the logging context variables.
   *
   * <p>In classic multithreaded environments, you can just use {@link
   * LoggingContextSetter#THREAD_LOCAL_INSTANCE}, though the caller of {@link RestateGrpcServer}
   * must take care of the cleanup of the thread local map.
   */
  public interface LoggingContextSetter {

    String INVOCATION_ID_KEY = "restateInvocationId";
    String SERVICE_METHOD_KEY = "restateServiceMethod";

    LoggingContextSetter THREAD_LOCAL_INSTANCE =
        new LoggingContextSetter() {
          @Override
          public void setServiceMethod(String serviceMethod) {
            ThreadContext.put(INVOCATION_ID_KEY, serviceMethod);
          }

          @Override
          public void setInvocationId(String id) {
            ThreadContext.put(SERVICE_METHOD_KEY, id);
          }
        };

    void setServiceMethod(String serviceMethod);

    void setInvocationId(String id);
  }
}
