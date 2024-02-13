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
import com.google.protobuf.Empty;
import com.google.protobuf.Value;
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.RestateService;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.syscalls.Syscalls;
import dev.restate.sdk.dynrpc.generated.KeyedRpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcResponse;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.template.generated.WorkflowGrpc;
import dev.restate.sdk.workflow.template.generated.WorkflowManagerGrpc;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

class ServiceImpl implements RestateService {

  private final WorkflowServicesBundle workflowServicesBundle;
  private final ServerServiceDefinition serverServiceDefinition;
  private final MethodDescriptor<StartRequest, StartResponse> workflowManagerTryStart;
  private final MethodDescriptor<SetOutputRequest, Empty> workflowManagerSetOutput;
  private final MethodDescriptor<InvokeRequest, Empty> workflowInternalStart;

  ServiceImpl(
      WorkflowServicesBundle workflowServicesBundle,
      WorkflowMangledDescriptors mangledDescriptors) {
    this.workflowServicesBundle = workflowServicesBundle;

    this.serverServiceDefinition =
        buildWorfklowServerServiceDefinition(
            mangledDescriptors.getOutputFileDescriptor(),
            mangledDescriptors.getWorkflowServiceSimpleName(),
            mangledDescriptors.getWorkflowServiceFqsn(),
            workflowServicesBundle.getSharedMethods());

    this.workflowManagerTryStart =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getTryStartMethod(), workflowServicesBundle.getName());
    this.workflowManagerSetOutput =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getSetOutputMethod(), workflowServicesBundle.getName());
    this.workflowInternalStart =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowInternalStart(
            workflowServicesBundle.getName());
  }

  @Override
  public final ServerServiceDefinition bindService() {
    return this.serverServiceDefinition;
  }

  private void handleKeyed(
      KeyedContext keyedContext,
      KeyedRpcRequest invokeRequest,
      StreamObserver<RpcResponse> streamObserver) {
    // Try start
    var response =
        keyedContext
            .call(
                workflowManagerTryStart,
                StartRequest.newBuilder().setKey(invokeRequest.getKey()).build())
            .await();
    if (response.getState().equals(WorkflowExecutionState.STARTED)) {
      // Schedule start
      keyedContext.oneWayCall(this.workflowInternalStart, invokeRequest);
    }

    replySuccess(SubmitResponse.newBuilder().setState(response.getState()).build(), streamObserver);
  }

  private void internalStart(
      KeyedContext keyedContext,
      InvokeRequest invokeRequest,
      StreamObserver<Empty> streamObserver) {
    // We can start now!
    Value valueOutput;
    try {
      // Convert input
      Object input =
          WorkflowCodegenUtil.valueToT(
              workflowServicesBundle.getSig().getRequestSerde(), invokeRequest.getPayload());

      // Invoke method
      WorkflowContext ctx =
          new WorkflowContextImpl(
              workflowServicesBundle.getName(), keyedContext, invokeRequest.getKey(), true);
      @SuppressWarnings("unchecked")
      Object output =
          ((BiFunction<WorkflowContext, Object, Object>) workflowServicesBundle.getRunner())
              .apply(ctx, input);

      //noinspection unchecked
      valueOutput =
          WorkflowCodegenUtil.tToValue(
              (Serde<? super Object>) workflowServicesBundle.getSig().getResponseSerde(), output);
    } catch (TerminalException e) {
      // Intercept TerminalException to record it
      keyedContext.oneWayCall(
          workflowManagerSetOutput,
          SetOutputRequest.newBuilder()
              .setKey(invokeRequest.getKey())
              .setOutput(
                  MethodOutput.newBuilder()
                      .setFailure(
                          Failure.newBuilder()
                              .setCode(e.getCode().value())
                              .setMessage(e.getMessage())))
              .build());
      throw e;
    }

    // Record output
    keyedContext.oneWayCall(
        workflowManagerSetOutput,
        SetOutputRequest.newBuilder()
            .setKey(invokeRequest.getKey())
            .setOutput(MethodOutput.newBuilder().setValue(valueOutput))
            .build());

    replySuccess(Empty.getDefaultInstance(), streamObserver);
  }

  private void invokeSharedMethod(
      String methodName,
      KeyedContext context,
      InvokeRequest request,
      StreamObserver<Value> streamObserver) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    WorkflowServicesBundle.Method<Object, Object> method =
        (WorkflowServicesBundle.Method<Object, Object>)
            workflowServicesBundle.getSharedMethod(methodName);
    if (method == null) {
      throw new TerminalException(
          TerminalException.Code.NOT_FOUND, "Method " + methodName + " not found");
    }

    // Convert input
    Object input =
        WorkflowCodegenUtil.valueToT(
            method.getMethodSignature().getRequestSerde(), request.getPayload());

    // Invoke method
    WorkflowContext ctx =
        new WorkflowContextImpl(workflowServicesBundle.getName(), context, request.getKey(), false);
    // We let the sdk core to manage the failures
    Object output = method.run(ctx, input);

    replySuccess(
        WorkflowCodegenUtil.tToValue(method.getMethodSignature().getResponseSerde(), output),
        streamObserver);
  }

  private <T> void replySuccess(T value, StreamObserver<T> streamObserver) {
    streamObserver.onNext(value);
    streamObserver.onCompleted();
  }

  private ServerServiceDefinition buildWorfklowServerServiceDefinition(
      Descriptors.FileDescriptor outputFileDescriptor,
      String simpleName,
      String fqsn,
      Set<String> methodNames) {
    var adapterDescriptorSupplier =
        new DescriptorUtils.AdapterServiceDescriptorSupplier(outputFileDescriptor, simpleName);
    ServiceDescriptor.Builder grpcServiceDescriptorBuilder =
        ServiceDescriptor.newBuilder(fqsn).setSchemaDescriptor(adapterDescriptorSupplier);

    var methodDescriptors = List.copyOf(WorkflowGrpc.getServiceDescriptor().getMethods());
    assert methodDescriptors.size() == 3;

    // Add Submit and InternalStart method
    @SuppressWarnings("unchecked")
    MethodDescriptor<InvokeRequest, SubmitResponse> submitMethodDescriptor =
        (MethodDescriptor<InvokeRequest, SubmitResponse>)
            methodDescriptors.get(0).toBuilder()
                .setSchemaDescriptor(
                    new DescriptorUtils.AdapterMethodDescriptorSupplier(
                        outputFileDescriptor,
                        simpleName,
                        methodDescriptors.get(0).getBareMethodName()))
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName(
                        fqsn, methodDescriptors.get(0).getBareMethodName()))
                .build();
    grpcServiceDescriptorBuilder.addMethod(submitMethodDescriptor);
    @SuppressWarnings("unchecked")
    MethodDescriptor<InvokeRequest, Empty> internalStartMethodDescriptor =
        (MethodDescriptor<InvokeRequest, Empty>)
            methodDescriptors.get(1).toBuilder()
                .setSchemaDescriptor(
                    new DescriptorUtils.AdapterMethodDescriptorSupplier(
                        outputFileDescriptor,
                        simpleName,
                        methodDescriptors.get(1).getBareMethodName()))
                .setFullMethodName(
                    MethodDescriptor.generateFullMethodName(
                        fqsn, methodDescriptors.get(1).getBareMethodName()))
                .build();
    grpcServiceDescriptorBuilder.addMethod(internalStartMethodDescriptor);

    // Compute shared methods
    MethodDescriptor<?, ?> invokeTemplateDescriptor = methodDescriptors.get(2);
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

    // Add submit and internal start method
    serverServiceDefinitionBuilder.addMethod(
        submitMethodDescriptor,
        ServerCalls.asyncUnaryCall(
            (invokeRequest, streamObserver) ->
                this.submit(
                    KeyedContext.fromSyscalls(Syscalls.current()), invokeRequest, streamObserver)));
    serverServiceDefinitionBuilder.addMethod(
        internalStartMethodDescriptor,
        ServerCalls.asyncUnaryCall(
            (invokeRequest, streamObserver) ->
                this.internalStart(
                    KeyedContext.fromSyscalls(Syscalls.current()), invokeRequest, streamObserver)));

    // Compute shared methods
    for (var method : methods.entrySet()) {
      @SuppressWarnings("unchecked")
      MethodDescriptor<InvokeRequest, Value> desc =
          (MethodDescriptor<InvokeRequest, Value>) methods.get(method.getKey());
      ServerCallHandler<InvokeRequest, Value> handler =
          ServerCalls.asyncUnaryCall(
              (invokeRequest, streamObserver) ->
                  this.invokeSharedMethod(
                      method.getKey(),
                      KeyedContext.fromSyscalls(Syscalls.current()),
                      invokeRequest,
                      streamObserver));

      serverServiceDefinitionBuilder.addMethod(desc, handler);
    }

    return serverServiceDefinitionBuilder.build();
  }
}
