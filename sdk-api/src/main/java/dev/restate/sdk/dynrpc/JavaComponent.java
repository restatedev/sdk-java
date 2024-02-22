// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.dynrpc;

import com.google.protobuf.Descriptors;
import dev.restate.sdk.Component;
import dev.restate.sdk.Context;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.*;
import dev.restate.sdk.common.ComponentType;
import dev.restate.sdk.common.syscalls.ComponentDefinition;
import dev.restate.sdk.common.syscalls.Syscalls;
import dev.restate.sdk.dynrpc.generated.KeyedRpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcResponse;
import dev.restate.sdk.dynrpc.template.generated.KeyedServiceGrpc;
import dev.restate.sdk.dynrpc.template.generated.ServiceGrpc;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import java.util.*;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public class JavaComponent implements Component, ComponentBundle {
  private final String name;
  private final HashMap<String, Handler<?, ?>> handlers;
  private final ServerServiceDefinition serverServiceDefinition;
  private final ComponentDefinition componentDefinition;

  private JavaComponent(String fqsn, boolean isKeyed, HashMap<String, Handler<?, ?>> handlers) {
    this.name = fqsn;
    this.handlers = handlers;

    String simpleName = getSimpleName(fqsn);
    String packageName = getPackageName(fqsn);

    this.serverServiceDefinition =
        buildServerServiceDefinition(
            DescriptorUtils.mangle(packageName, simpleName, handlers.keySet(), isKeyed),
            simpleName,
            fqsn,
            handlers.keySet(),
            isKeyed);

    this.componentDefinition =
        new ComponentDefinition(
            fqsn,
            ComponentDefinition.ExecutorType.BLOCKING,
            isKeyed ? ComponentType.VIRTUAL_OBJECT : ComponentType.SERVICE,
            Collections.emptyList() // TODO
            );
  }

  private Handler<?, ?> getHandler(String name) {
    return handlers.get(name);
  }

  public String getName() {
    return name;
  }

  @Override
  public ComponentDefinition definition() {
    return this.componentDefinition;
  }

  @Override
  public ServerServiceDefinition bindService() {
    return this.serverServiceDefinition;
  }

  public static StatelessServiceBuilder service(String name) {
    return new StatelessServiceBuilder(name);
  }

  public static ObjectServiceBuilder virtualObject(String name) {
    return new ObjectServiceBuilder(name);
  }

  @Override
  public List<BlockingComponent> components() {
    return List.of(this);
  }

  public static class ObjectServiceBuilder {
    private final String name;
    private final HashMap<String, Handler<?, ?>> handlers;

    ObjectServiceBuilder(String name) {
      this.name = name;
      this.handlers = new HashMap<>();
    }

    public <REQ, RES> ObjectServiceBuilder with(
        HandlerSignature<REQ, RES> sig, BiFunction<ObjectContext, REQ, RES> runner) {
      this.handlers.put(sig.getMethod(), new Handler<>(sig, runner));
      return this;
    }

    public JavaComponent build() {
      return new JavaComponent(this.name, true, this.handlers);
    }
  }

  public static class StatelessServiceBuilder {
    private final String name;
    private final HashMap<String, Handler<?, ?>> methods;

    StatelessServiceBuilder(String name) {
      this.name = name;
      this.methods = new HashMap<>();
    }

    public <REQ, RES> StatelessServiceBuilder with(
        HandlerSignature<REQ, RES> sig, BiFunction<Context, REQ, RES> runner) {
      this.methods.put(sig.getMethod(), new Handler<>(sig, runner));
      return this;
    }

    public JavaComponent build() {
      return new JavaComponent(this.name, false, this.methods);
    }
  }

  @SuppressWarnings("unchecked")
  public static class Handler<REQ, RES> {
    private final HandlerSignature<REQ, RES> handlerSignature;

    private final BiFunction<Context, REQ, RES> runner;

    Handler(
        HandlerSignature<REQ, RES> handlerSignature,
        BiFunction<? extends Context, REQ, RES> runner) {
      this.handlerSignature = handlerSignature;
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public HandlerSignature<REQ, RES> getHandlerSignature() {
      return handlerSignature;
    }

    public RES run(Context ctx, REQ req) {
      return runner.apply(ctx, req);
    }
  }

  public static class HandlerSignature<REQ, RES> {

    private final String method;
    private final Serde<REQ> requestSerde;
    private final Serde<RES> responseSerde;

    HandlerSignature(String method, Serde<REQ> requestSerde, Serde<RES> responseSerde) {
      this.method = method;
      this.requestSerde = requestSerde;
      this.responseSerde = responseSerde;
    }

    public static <T, R> HandlerSignature<T, R> of(
        String method, Serde<T> requestSerde, Serde<R> responseSerde) {
      return new HandlerSignature<>(method, requestSerde, responseSerde);
    }

    public String getMethod() {
      return method;
    }

    public Serde<REQ> getRequestSerde() {
      return requestSerde;
    }

    public Serde<RES> getResponseSerde() {
      return responseSerde;
    }
  }

  private static String getSimpleName(String name) {
    return name.substring(name.lastIndexOf(".") + 1);
  }

  private static @Nullable String getPackageName(String name) {
    int i = name.lastIndexOf(".");
    if (i < 0) {
      return null;
    }
    return name.substring(0, i);
  }

  private ServerServiceDefinition buildServerServiceDefinition(
      Descriptors.FileDescriptor outputFileDescriptor,
      String simpleName,
      String fqsn,
      Set<String> methodNames,
      boolean isKeyed) {
    var adapterDescriptorSupplier =
        new DescriptorUtils.AdapterServiceDescriptorSupplier(outputFileDescriptor, simpleName);
    ServiceDescriptor.Builder grpcServiceDescriptorBuilder =
        ServiceDescriptor.newBuilder(fqsn).setSchemaDescriptor(adapterDescriptorSupplier);

    var methodDescriptors =
        List.copyOf(
            (isKeyed ? KeyedServiceGrpc.getServiceDescriptor() : ServiceGrpc.getServiceDescriptor())
                .getMethods());
    assert methodDescriptors.size() == 1;

    // Compute methods
    MethodDescriptor<?, ?> invokeTemplateDescriptor = methodDescriptors.get(0);
    Map<String, MethodDescriptor<?, ?>> methods = new HashMap<>();
    for (String methodName : methodNames) {
      var newMethodDescriptor =
          invokeTemplateDescriptor.toBuilder()
              .setSchemaDescriptor(
                  new DescriptorUtils.AdapterMethodDescriptorSupplier(
                      outputFileDescriptor, simpleName, methodName))
              .setFullMethodName(MethodDescriptor.generateFullMethodName(fqsn, methodName))
              .build();
      methods.put(methodName, newMethodDescriptor);
      grpcServiceDescriptorBuilder.addMethod(newMethodDescriptor);
    }

    ServiceDescriptor grpcServiceDescriptor = grpcServiceDescriptorBuilder.build();

    ServerServiceDefinition.Builder serverServiceDefinitionBuilder =
        ServerServiceDefinition.builder(grpcServiceDescriptor);

    // Compute shared methods
    for (var method : methods.entrySet()) {
      if (isKeyed) {
        @SuppressWarnings("unchecked")
        MethodDescriptor<KeyedRpcRequest, RpcResponse> desc =
            (MethodDescriptor<KeyedRpcRequest, RpcResponse>) methods.get(method.getKey());
        ServerCallHandler<KeyedRpcRequest, RpcResponse> handler =
            ServerCalls.asyncUnaryCall(
                (invokeRequest, streamObserver) ->
                    this.invokeKeyed(
                        method.getKey(),
                        ObjectContext.fromSyscalls(Syscalls.current()),
                        invokeRequest,
                        streamObserver));

        serverServiceDefinitionBuilder.addMethod(desc, handler);
      } else {
        @SuppressWarnings("unchecked")
        MethodDescriptor<RpcRequest, RpcResponse> desc =
            (MethodDescriptor<RpcRequest, RpcResponse>) methods.get(method.getKey());
        ServerCallHandler<RpcRequest, RpcResponse> handler =
            ServerCalls.asyncUnaryCall(
                (invokeRequest, streamObserver) ->
                    this.invokeUnkeyed(
                        method.getKey(),
                        Context.fromSyscalls(Syscalls.current()),
                        invokeRequest,
                        streamObserver));

        serverServiceDefinitionBuilder.addMethod(desc, handler);
      }
    }

    return serverServiceDefinitionBuilder.build();
  }

  private void invokeKeyed(
      String methodName,
      ObjectContext objectContext,
      KeyedRpcRequest invokeRequest,
      StreamObserver<RpcResponse> streamObserver) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    Handler<Object, Object> handler = (Handler<Object, Object>) this.getHandler(methodName);
    if (handler == null) {
      throw new TerminalException(
          TerminalException.Code.NOT_FOUND, "Method " + methodName + " not found");
    }

    // Convert input
    Object input =
        CodegenUtils.valueToT(
            handler.getHandlerSignature().getRequestSerde(), invokeRequest.getRequest());

    // Invoke method
    // We let the sdk core to manage the failures

    // TODO add key to context?
    Object output = handler.run(objectContext, input);

    replySuccess(
        RpcResponse.newBuilder()
            .setResponse(
                CodegenUtils.tToValue(handler.getHandlerSignature().getResponseSerde(), output))
            .build(),
        streamObserver);
  }

  private void invokeUnkeyed(
      String methodName,
      Context context,
      RpcRequest invokeRequest,
      StreamObserver<RpcResponse> streamObserver) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    Handler<Object, Object> handler = (Handler<Object, Object>) this.getHandler(methodName);
    if (handler == null) {
      throw new TerminalException(
          TerminalException.Code.NOT_FOUND, "Method " + methodName + " not found");
    }

    // Convert input
    Object input =
        CodegenUtils.valueToT(
            handler.getHandlerSignature().getRequestSerde(), invokeRequest.getRequest());

    // Invoke method
    // We let the sdk core to manage the failures
    Object output = handler.run(context, input);

    replySuccess(
        RpcResponse.newBuilder()
            .setResponse(
                CodegenUtils.tToValue(handler.getHandlerSignature().getResponseSerde(), output))
            .build(),
        streamObserver);
  }

  private <T> void replySuccess(T value, StreamObserver<T> streamObserver) {
    streamObserver.onNext(value);
    streamObserver.onCompleted();
  }
}
