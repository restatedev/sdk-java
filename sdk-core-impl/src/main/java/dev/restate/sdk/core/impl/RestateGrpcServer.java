package dev.restate.sdk.core.impl;

import com.google.protobuf.MessageLite;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RestateGrpcServer {

  private static final Logger LOG = LogManager.getLogger(RestateGrpcServer.class);

  private final Map<String, ServerServiceDefinition> services;
  private final Serde serde;
  private final Tracer tracer;

  private RestateGrpcServer(
      Map<String, ServerServiceDefinition> services, Serde serde, Tracer tracer) {
    this.services = services;
    this.serde = serde;
    this.tracer = tracer;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public InvocationHandler resolve(
      String serviceName,
      String methodName,
      io.opentelemetry.context.Context otelContext,
      Function<SyscallsInternal, SyscallsInternal> syscallsDecorator,
      Function<ServerCall.Listener<MessageLite>, ServerCall.Listener<MessageLite>>
          serverCallListenerDecorator)
      throws ProtocolException {
    // Resolve the service method definition
    ServerServiceDefinition svc = this.services.get(serviceName);
    if (svc == null) {
      throw ProtocolException.methodNotFound(serviceName, methodName);
    }
    ServerMethodDefinition<MessageLite, MessageLite> method =
        (ServerMethodDefinition) svc.getMethod(serviceName + "/" + methodName);
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

    // Instantiate state machine, syscall and grpc bridge
    InvocationStateMachine stateMachine = new InvocationStateMachine(serviceName, span);
    SyscallsInternal syscalls = syscallsDecorator.apply(new SyscallsImpl(stateMachine, this.serde));
    RestateServerCall bridge = new RestateServerCall(method.getMethodDescriptor(), syscalls);

    return new InvocationHandler() {
      @Override
      public InvocationFlow.InvocationProcessor processor() {
        return stateMachine;
      }

      @Override
      public void start() {
        LOG.debug("Start processing call to {}/{}", serviceName, methodName);
        stateMachine.start(
            () -> {
              // Create the listener and create the decorators chain
              ServerCall.Listener<MessageLite> listener =
                  Contexts.interceptCall(
                      Context.current().withValue(Syscalls.SYSCALLS_KEY, syscalls),
                      bridge,
                      new Metadata(),
                      method.getServerCallHandler());
              listener = new ExceptionCatchingServerCallListener<>(listener, bridge);
              listener = serverCallListenerDecorator.apply(listener);

              bridge.setListener(listener);
            });
      }
    };
  }

  // -- Builder

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private final List<ServerServiceDefinition> services = new ArrayList<>();
    private Serde serde; // TODO discover Serde here!!!
    private Tracer tracer = OpenTelemetry.noop().getTracer("NOOP");

    public Builder withService(BindableService service) {
      this.services.add(service.bindService());
      return this;
    }

    public Builder withService(ServerServiceDefinition service) {
      this.services.add(service);
      return this;
    }

    public Builder withSerde(Serde serde) {
      this.serde = serde;
      return this;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public RestateGrpcServer build() {
      return new RestateGrpcServer(
          this.services.stream()
              .collect(
                  Collectors.toMap(
                      svc -> svc.getServiceDescriptor().getName(), Function.identity())),
          serde,
          tracer);
    }
  }
}
