// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import com.google.protobuf.UnsafeByteOperations;
import dev.restate.sdk.KeyedContext;
import dev.restate.sdk.common.*;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import dev.restate.sdk.workflow.generated.*;
import dev.restate.sdk.workflow.template.generated.WorkflowManagerRestate;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import java.util.*;

class WorkflowManagerImpl extends WorkflowManagerRestate.WorkflowManagerRestateImplBase {

  private static final Serde<DurablePromiseCompletion> DURABLEPROMISE_COMPLETION_SERDE =
      CoreSerdes.ofProtobuf(DurablePromiseCompletion.parser());
  private static final Serde<Set<String>> DURABLEPROMISE_LISTENER_SERDE =
      JacksonSerdes.of(new TypeReference<>() {});
  private static final StateKey<MethodOutput> OUTPUT_KEY =
      StateKey.of("_output", CoreSerdes.ofProtobuf(MethodOutput.parser()));

  private static final StateKey<StartResponse> WORKFLOW_EXECUTION_STATE_KEY =
      StateKey.of("_workflow_execution_state", CoreSerdes.ofProtobuf(StartResponse.parser()));

  @Override
  public GetStateResponse getState(KeyedContext context, StateRequest request)
      throws TerminalException {
    return context
        .get(stateKey(request.getStateKey()))
        .map(
            val ->
                GetStateResponse.newBuilder()
                    .setValue(UnsafeByteOperations.unsafeWrap(val))
                    .build())
        .orElseGet(
            () -> GetStateResponse.newBuilder().setEmpty(Empty.getDefaultInstance()).build());
  }

  @Override
  public void setState(KeyedContext context, SetStateRequest request) throws TerminalException {
    context.set(stateKey(request.getStateKey()), request.getStateValue().toByteArray());
  }

  @Override
  public void clearState(KeyedContext context, StateRequest request) throws TerminalException {
    context.clear(stateKey(request.getStateKey()));
  }

  private StateKey<byte[]> stateKey(String key) {
    return StateKey.raw("_state_" + key);
  }

  @Override
  public void waitDurablePromiseCompletion(
      KeyedContext context, WaitDurablePromiseCompletionRequest request) throws TerminalException {
    Optional<DurablePromiseCompletion> val =
        context.get(durablePromiseKey(request.getDurablePromiseKey()));
    if (val.isPresent()) {
      completeListener(context, request.getAwakeableId(), val.get());
      return;
    }

    StateKey<Set<String>> listenersKey = durablePromiseListenersKey(request.getDurablePromiseKey());
    Set<String> listeners = context.get(listenersKey).orElseGet(HashSet::new);
    listeners.add(request.getAwakeableId());
    context.set(listenersKey, listeners);
  }

  @Override
  public MaybeDurablePromiseCompletion getDurablePromiseCompletion(
      KeyedContext context, GetDurablePromiseCompletionRequest request) throws TerminalException {
    StateKey<DurablePromiseCompletion> durablePromiseKey =
        durablePromiseKey(request.getDurablePromiseKey());
    Optional<DurablePromiseCompletion> val = context.get(durablePromiseKey);
    if (val.isEmpty()) {
      return MaybeDurablePromiseCompletion.newBuilder()
          .setNotCompleted(Empty.getDefaultInstance())
          .build();
    }
    if (val.get().hasValue()) {
      return MaybeDurablePromiseCompletion.newBuilder().setValue(val.get().getValue()).build();
    }
    return MaybeDurablePromiseCompletion.newBuilder().setFailure(val.get().getFailure()).build();
  }

  @Override
  public void completeDurablePromise(KeyedContext context, CompleteDurablePromiseRequest request)
      throws TerminalException {
    // User can decide whether they want to allow overwriting the previously resolved value or not
    StateKey<DurablePromiseCompletion> durablePromiseKey =
        durablePromiseKey(request.getDurablePromiseKey());
    Optional<DurablePromiseCompletion> val = context.get(durablePromiseKey);
    if (val.isPresent()) {
      throw new TerminalException("Can't complete an already completed durablePromise");
    }
    context.set(durablePromiseKey, request.getCompletion());

    StateKey<Set<String>> listenersKey = durablePromiseListenersKey(request.getDurablePromiseKey());
    Set<String> listeners = context.get(listenersKey).orElse(Collections.emptySet());
    for (String listener : listeners) {
      completeListener(context, listener, request.getCompletion());
    }
    context.clear(listenersKey);
  }

  private void completeListener(
      KeyedContext context, String listener, DurablePromiseCompletion completion) {
    if (completion.hasValue()) {
      context
          .awakeableHandle(listener)
          .resolve(CoreSerdes.RAW, completion.getValue().toByteArray());
    } else {
      context.awakeableHandle(listener).reject(completion.getFailure().getMessage());
    }
  }

  @Override
  public StartResponse tryStart(KeyedContext context, StartRequest request)
      throws TerminalException {
    Optional<StartResponse> maybeResponse = context.get(WORKFLOW_EXECUTION_STATE_KEY);
    if (maybeResponse.isPresent()) {
      return maybeResponse.get();
    }

    context.set(
        WORKFLOW_EXECUTION_STATE_KEY,
        StartResponse.newBuilder().setState(WorkflowExecutionState.ALREADY_STARTED).build());
    return StartResponse.newBuilder().setState(WorkflowExecutionState.STARTED).build();
  }

  @Override
  public GetOutputResponse getOutput(KeyedContext context, OutputRequest request)
      throws TerminalException {
    return context
        .get(OUTPUT_KEY)
        .map(
            methodOutput ->
                methodOutput.hasValue()
                    ? GetOutputResponse.newBuilder().setValue(methodOutput.getValue()).build()
                    : GetOutputResponse.newBuilder().setFailure(methodOutput.getFailure()).build())
        .orElseGet(
            () ->
                GetOutputResponse.newBuilder().setNotCompleted(Empty.getDefaultInstance()).build());
  }

  @Override
  public void setOutput(KeyedContext context, SetOutputRequest request) throws TerminalException {
    context.set(OUTPUT_KEY, request.getOutput());
    context.set(
        WORKFLOW_EXECUTION_STATE_KEY,
        StartResponse.newBuilder().setState(WorkflowExecutionState.ALREADY_COMPLETED).build());
  }

  @Override
  public void cleanup(KeyedContext context, WorkflowManagerRequest request)
      throws TerminalException {
    context.clearAll();
  }

  private StateKey<DurablePromiseCompletion> durablePromiseKey(String key) {
    return StateKey.of("_durablePromise_" + key, DURABLEPROMISE_COMPLETION_SERDE);
  }

  private StateKey<Set<String>> durablePromiseListenersKey(String key) {
    return StateKey.of("_durablePromise_listeners_" + key, DURABLEPROMISE_LISTENER_SERDE);
  }

  static BlockingService create(
      Descriptors.FileDescriptor outputFileDescriptor, String simpleName, String fqsn) {
    WorkflowManagerImpl workflowManager = new WorkflowManagerImpl();
    ServerServiceDefinition originalDefinition = workflowManager.bindService();

    var adapterDescriptorSupplier =
        new DescriptorUtils.AdapterServiceDescriptorSupplier(outputFileDescriptor, simpleName);
    ServiceDescriptor.Builder grpcServiceDescriptorBuilder =
        ServiceDescriptor.newBuilder(fqsn).setSchemaDescriptor(adapterDescriptorSupplier);

    Map<String, MethodDescriptor<?, ?>> methods = new HashMap<>();
    for (var originalMethodDescriptor : originalDefinition.getServiceDescriptor().getMethods()) {
      var newMethodDescriptor =
          originalMethodDescriptor.toBuilder()
              .setSchemaDescriptor(
                  new DescriptorUtils.AdapterMethodDescriptorSupplier(
                      outputFileDescriptor,
                      simpleName,
                      originalMethodDescriptor.getBareMethodName()))
              .setFullMethodName(
                  MethodDescriptor.generateFullMethodName(
                      fqsn, Objects.requireNonNull(originalMethodDescriptor.getBareMethodName())))
              .build();
      methods.put(originalMethodDescriptor.getBareMethodName(), newMethodDescriptor);
      grpcServiceDescriptorBuilder.addMethod(newMethodDescriptor);
    }
    ServiceDescriptor grpcServiceDescriptor = grpcServiceDescriptorBuilder.build();

    ServerServiceDefinition.Builder serverServiceDefinitionBuilder =
        ServerServiceDefinition.builder(grpcServiceDescriptor);
    for (var method : originalDefinition.getMethods()) {
      //noinspection unchecked
      serverServiceDefinitionBuilder.addMethod(
          (MethodDescriptor<Object, Object>)
              methods.get(method.getMethodDescriptor().getBareMethodName()),
          (ServerCallHandler<Object, Object>) method.getServerCallHandler());
    }
    ServerServiceDefinition result = serverServiceDefinitionBuilder.build();
    return () -> result;
  }
}
