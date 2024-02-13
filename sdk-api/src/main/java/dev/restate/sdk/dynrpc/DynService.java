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
import dev.restate.sdk.Context;
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.RestateService;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.TerminalException;
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

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class DynService implements RestateService {
  private final String name;
  private final HashMap<String, Method<?, ?>> methods;
  private final ServerServiceDefinition serverServiceDefinition;

  public DynService(
          String fqsn,
          boolean isKeyed,
          HashMap<String, Method<?, ?>> methods) {
    this.name = fqsn;
    this.methods = methods;

      String simpleName = getSimpleName(fqsn);
      String packageName = getPackageName(fqsn);

      this.serverServiceDefinition = buildServerServiceDefinition(
              DescriptorUtils.mangle(packageName, simpleName, methods.keySet(), isKeyed),
              simpleName,
              fqsn,
              methods.keySet(),
              isKeyed
      );
  }

  private Method<?, ?> getMethod(String name) {
    return methods.get(name);
  }

  public String getName() {
    return name;
  }

  @Override
  public ServerServiceDefinition bindService() {
    return this.serverServiceDefinition;
  }

  public static DynServiceBuilder unkeyed(
      String name) {
    return new DynServiceBuilder(name, false);
  }

  public static DynServiceBuilder keyed(
          String name) {
    return new DynServiceBuilder(name, true);
  }

  public static class DynServiceBuilder {
    private final String name;
    private final boolean isKeyed;
    private final HashMap<String, Method<?, ?>> methods;

    DynServiceBuilder(String name, boolean isKeyed) {
      this.name = name;
        this.isKeyed = isKeyed;
        this.methods = new HashMap<>();
    }

    public <REQ, RES> DynServiceBuilder with(
        MethodSignature<REQ, RES> sig, BiFunction<Context, REQ, RES> runner) {
      this.methods.put(sig.getMethod(), new Method<>(sig, runner));
      return this;
    }

    public DynService build() {
      return new DynService(this.name, this.isKeyed, this.methods);
    }
  }

  @SuppressWarnings("unchecked")
  public static class Method<REQ, RES> {
    private final MethodSignature<REQ, RES> methodSignature;

    private final BiFunction<Context, REQ, RES> runner;

    Method(
        MethodSignature<REQ, RES> methodSignature, BiFunction<? extends Context, REQ, RES> runner) {
      this.methodSignature = methodSignature;
      this.runner = (BiFunction<Context, REQ, RES>) runner;
    }

    public MethodSignature<REQ, RES> getMethodSignature() {
      return methodSignature;
    }

    public RES run(Context ctx, REQ req) {
      return runner.apply(ctx, req);
    }
  }

  public static class MethodSignature<REQ, RES> {

    private final String method;
    private final Serde<REQ> requestSerde;
    private final Serde<RES> responseSerde;

    MethodSignature(String method, Serde<REQ> requestSerde, Serde<RES> responseSerde) {
      this.method = method;
      this.requestSerde = requestSerde;
      this.responseSerde = responseSerde;
    }

    public static <T, R> MethodSignature<T, R> of(
        String method, Serde<T> requestSerde, Serde<R> responseSerde) {
      return new MethodSignature<>(method, requestSerde, responseSerde);
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

  private static  String getSimpleName(String name ) {
    return name.substring(name.lastIndexOf(".") + 1);
  }

  private static @Nullable String getPackageName(String name ) {
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
          Set<String> methodNames, boolean isKeyed) {
    var adapterDescriptorSupplier =
            new DescriptorUtils.AdapterServiceDescriptorSupplier(outputFileDescriptor, simpleName);
    ServiceDescriptor.Builder grpcServiceDescriptorBuilder =
            ServiceDescriptor.newBuilder(fqsn).setSchemaDescriptor(adapterDescriptorSupplier);

    var methodDescriptors = List.copyOf((isKeyed ? KeyedServiceGrpc.getServiceDescriptor() : ServiceGrpc.getServiceDescriptor()).getMethods());
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
                                        KeyedContext.fromSyscalls(Syscalls.current()),
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

  private void invokeKeyed(String methodName, KeyedContext keyedContext, KeyedRpcRequest invokeRequest, StreamObserver<RpcResponse> streamObserver) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    DynService.Method<Object, Object> method =
            (DynService.Method<Object, Object>)
                    this.getMethod(methodName);
    if (method == null) {
      throw new TerminalException(
              TerminalException.Code.NOT_FOUND, "Method " + methodName + " not found");
    }

    // Convert input
    Object input =
            CodegenUtils.valueToT(
                    method.getMethodSignature().getRequestSerde(), invokeRequest.getRequest());

    // Invoke method
    // We let the sdk core to manage the failures

    // TODO add key to context?
    Object output = method.run(keyedContext, input);

    replySuccess(
            RpcResponse.newBuilder().setResponse(CodegenUtils.tToValue(method.getMethodSignature().getResponseSerde(), output)).build(),
            streamObserver);
  }

  private void invokeUnkeyed(String methodName, Context context, RpcRequest invokeRequest, StreamObserver<RpcResponse> streamObserver) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    DynService.Method<Object, Object> method =
            (DynService.Method<Object, Object>)
                    this.getMethod(methodName);
    if (method == null) {
      throw new TerminalException(
              TerminalException.Code.NOT_FOUND, "Method " + methodName + " not found");
    }

    // Convert input
    Object input =
            CodegenUtils.valueToT(
                    method.getMethodSignature().getRequestSerde(), invokeRequest.getRequest());

    // Invoke method
    // We let the sdk core to manage the failures
    Object output = method.run(context, input);

    replySuccess(
            RpcResponse.newBuilder().setResponse(CodegenUtils.tToValue(method.getMethodSignature().getResponseSerde(), output)).build(),
            streamObserver);
  }

  private <T> void replySuccess(T value, StreamObserver<T> streamObserver) {
    streamObserver.onNext(value);
    streamObserver.onCompleted();
  }
}
