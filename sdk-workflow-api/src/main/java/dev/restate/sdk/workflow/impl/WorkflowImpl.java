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
import com.google.protobuf.*;
import dev.restate.sdk.Context;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.Service;
import dev.restate.sdk.Service.HandlerSignature;
import dev.restate.sdk.common.*;
import dev.restate.sdk.common.syscalls.ServiceDefinition;
import dev.restate.sdk.serde.jackson.JacksonSerdes;
import dev.restate.sdk.serde.protobuf.ProtobufSerdes;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.WorkflowExecutionState;
import dev.restate.sdk.workflow.generated.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

public class WorkflowImpl implements BindableService<Service.Options> {

  public static final Serde<InvokeRequest> INVOKE_REQUEST_SERDE =
      JacksonSerdes.of(InvokeRequest.class);
  static final Serde<WorkflowExecutionState> WORKFLOW_EXECUTION_STATE_SERDE =
      JacksonSerdes.of(WorkflowExecutionState.class);
  static final Serde<GetStateResponse> GET_STATE_RESPONSE_SERDE =
      ProtobufSerdes.of(GetStateResponse.parser());
  static final Serde<SetStateRequest> SET_STATE_REQUEST_SERDE =
      ProtobufSerdes.of(SetStateRequest.parser());
  static final Serde<WaitDurablePromiseCompletionRequest>
      WAIT_DURABLE_PROMISE_COMPLETION_REQUEST_SERDE =
          ProtobufSerdes.of(WaitDurablePromiseCompletionRequest.parser());
  static final Serde<MaybeDurablePromiseCompletion> MAYBE_DURABLE_PROMISE_COMPLETION_SERDE =
      ProtobufSerdes.of(MaybeDurablePromiseCompletion.parser());
  static final Serde<CompleteDurablePromiseRequest> COMPLETE_DURABLE_PROMISE_REQUEST_SERDE =
      ProtobufSerdes.of(CompleteDurablePromiseRequest.parser());
  static final Serde<GetOutputResponse> GET_OUTPUT_RESPONSE_SERDE =
      ProtobufSerdes.of(GetOutputResponse.parser());
  private static final Serde<SetOutputRequest> SET_OUTPUT_REQUEST_SERDE =
      ProtobufSerdes.of(SetOutputRequest.parser());
  private static final Serde<DurablePromiseCompletion> DURABLE_PROMISE_COMPLETION_SERDE =
      ProtobufSerdes.of(DurablePromiseCompletion.parser());

  private static final Serde<Set<String>> DURABLEPROMISE_LISTENER_SERDE =
      JacksonSerdes.of(new TypeReference<>() {});
  private static final StateKey<MethodOutput> OUTPUT_KEY =
      StateKey.of("_output", ProtobufSerdes.of(MethodOutput.parser()));

  private static final StateKey<WorkflowExecutionState> WORKFLOW_EXECUTION_STATE_KEY =
      StateKey.of("_workflow_execution_state", WORKFLOW_EXECUTION_STATE_SERDE);
  private static final String START_HANDLER = "_start";

  private final String name;
  private final Service.Options options;
  private final Service.Handler<?, ?> workflowMethod;
  private final HashMap<String, Service.Handler<?, ?>> sharedHandlers;

  public WorkflowImpl(
      String name,
      Service.Options options,
      Service.Handler<?, ?> workflowMethod,
      HashMap<String, Service.Handler<?, ?>> sharedHandlers) {
    this.name = name;
    this.options = options;
    this.workflowMethod = workflowMethod;
    this.sharedHandlers = sharedHandlers;
  }

  // --- Workflow methods

  private WorkflowExecutionState submit(Context objectContext, InvokeRequest invokeRequest) {
    // Try start
    var response =
        objectContext
            .call(
                workflowManagerTarget(invokeRequest.getKey(), "tryStart"),
                CoreSerdes.JSON_STRING,
                WORKFLOW_EXECUTION_STATE_SERDE,
                invokeRequest.getKey())
            .await();
    if (response.equals(WorkflowExecutionState.STARTED)) {
      // Schedule start
      objectContext.send(
          Target.service(name, WorkflowImpl.START_HANDLER), INVOKE_REQUEST_SERDE, invokeRequest);
    }

    return response;
  }

  private void internalStart(Context context, InvokeRequest invokeRequest) {
    // We can start now!
    byte[] valueOutput;
    try {
      // Convert input
      Object input =
          this.workflowMethod
              .getHandlerSignature()
              .getRequestSerde()
              .deserialize(invokeRequest.getPayload().toString().getBytes(StandardCharsets.UTF_8));

      // Invoke run
      WorkflowContext ctx = new WorkflowContextImpl(context, name, invokeRequest.getKey(), true);
      @SuppressWarnings("unchecked")
      Object output =
          ((BiFunction<Context, Object, Object>) this.workflowMethod.getRunner()).apply(ctx, input);

      //noinspection unchecked
      valueOutput =
          ((Serde<Object>) this.workflowMethod.getHandlerSignature().getResponseSerde())
              .serialize(output);
    } catch (TerminalException e) {
      // Intercept TerminalException to record it
      context.send(
          workflowManagerTarget(invokeRequest.getKey(), "setOutput"),
          SET_OUTPUT_REQUEST_SERDE,
          SetOutputRequest.newBuilder()
              .setOutput(
                  MethodOutput.newBuilder()
                      .setFailure(
                          Failure.newBuilder().setCode(e.getCode()).setMessage(e.getMessage())))
              .build());
      throw e;
    }

    // Record output
    context.send(
        workflowManagerTarget(invokeRequest.getKey(), "setOutput"),
        SET_OUTPUT_REQUEST_SERDE,
        SetOutputRequest.newBuilder()
            .setOutput(
                MethodOutput.newBuilder().setValue(UnsafeByteOperations.unsafeWrap(valueOutput)))
            .build());
  }

  private byte[] invokeSharedMethod(String handlerName, Context context, InvokeRequest request) {
    // Lookup the method
    @SuppressWarnings("unchecked")
    Service.Handler<Object, Object> method =
        (Service.Handler<Object, Object>) sharedHandlers.get(handlerName);
    if (method == null) {
      throw new TerminalException(404, "Method " + handlerName + " not found");
    }

    // Convert input
    Object input =
        method
            .getHandlerSignature()
            .getRequestSerde()
            .deserialize(request.getPayload().toString().getBytes(StandardCharsets.UTF_8));

    // Invoke method
    WorkflowContext ctx = new WorkflowContextImpl(context, name, request.getKey(), false);
    // We let the sdk core to manage the failures
    Object output = method.getRunner().apply(ctx, input);

    return method.getHandlerSignature().getResponseSerde().serialize(output);
  }

  // --- Workflow manager methods

  private GetStateResponse getState(ObjectContext context, String key) throws TerminalException {
    return context
        .get(stateKey(key))
        .map(val -> GetStateResponse.newBuilder().setValue(val).build())
        .orElseGet(
            () -> GetStateResponse.newBuilder().setEmpty(Empty.getDefaultInstance()).build());
  }

  private void setState(ObjectContext context, SetStateRequest request) throws TerminalException {
    context.set(stateKey(request.getStateKey()), request.getStateValue());
  }

  private void clearState(ObjectContext context, String key) throws TerminalException {
    context.clear(stateKey(key));
  }

  private void waitDurablePromiseCompletion(
      ObjectContext context, WaitDurablePromiseCompletionRequest request) throws TerminalException {
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

  private MaybeDurablePromiseCompletion getDurablePromiseCompletion(
      ObjectContext context, String durablePromiseKeyStr) throws TerminalException {
    StateKey<DurablePromiseCompletion> durablePromiseKey = durablePromiseKey(durablePromiseKeyStr);
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

  private void completeDurablePromise(ObjectContext context, CompleteDurablePromiseRequest request)
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

  private WorkflowExecutionState tryStart(ObjectContext context) throws TerminalException {
    Optional<WorkflowExecutionState> maybeResponse = context.get(WORKFLOW_EXECUTION_STATE_KEY);
    if (maybeResponse.isPresent()) {
      return maybeResponse.get();
    }

    context.set(WORKFLOW_EXECUTION_STATE_KEY, WorkflowExecutionState.ALREADY_STARTED);
    return WorkflowExecutionState.STARTED;
  }

  private GetOutputResponse getOutput(ObjectContext context) throws TerminalException {
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

  private void setOutput(ObjectContext context, SetOutputRequest request) throws TerminalException {
    context.set(OUTPUT_KEY, request.getOutput());
    context.set(WORKFLOW_EXECUTION_STATE_KEY, WorkflowExecutionState.ALREADY_COMPLETED);
  }

  private void cleanup(ObjectContext context) throws TerminalException {
    context.clearAll();
  }

  // --- Util methods for WorkflowManager

  private StateKey<ByteString> stateKey(String key) {
    return StateKey.of(
        "_state_" + key,
        new Serde<>() {
          @Override
          public byte[] serialize(@Nullable ByteString value) {
            return value.toByteArray();
          }

          @Override
          public ByteString serializeToByteString(@Nullable ByteString value) {
            return value;
          }

          @Override
          public ByteString deserialize(ByteString byteString) {
            return byteString;
          }

          @Override
          public ByteString deserialize(byte[] value) {
            return UnsafeByteOperations.unsafeWrap(value);
          }
        });
  }

  private void completeListener(
      ObjectContext context, String listener, DurablePromiseCompletion completion) {
    if (completion.hasValue()) {
      context
          .awakeableHandle(listener)
          .resolve(CoreSerdes.RAW, completion.getValue().toByteArray());
    } else {
      context.awakeableHandle(listener).reject(completion.getFailure().getMessage());
    }
  }

  private StateKey<DurablePromiseCompletion> durablePromiseKey(String key) {
    return StateKey.of("_durablePromise_" + key, DURABLE_PROMISE_COMPLETION_SERDE);
  }

  private StateKey<Set<String>> durablePromiseListenersKey(String key) {
    return StateKey.of("_durablePromise_listeners_" + key, DURABLEPROMISE_LISTENER_SERDE);
  }

  static String workflowManagerObjectName(String workflowName) {
    return workflowName + "_Manager";
  }

  private Target workflowManagerTarget(String key, String handler) {
    return Target.virtualObject(workflowManagerObjectName(name), key, handler);
  }

  // --- Services definition

  @Override
  public Service.Options options() {
    return options;
  }

  @Override
  public List<ServiceDefinition<Service.Options>> definitions() {
    // Prepare workflow service
    Service.ServiceBuilder workflowBuilder =
        Service.service(name)
            .with(
                HandlerSignature.of("submit", INVOKE_REQUEST_SERDE, WORKFLOW_EXECUTION_STATE_SERDE),
                this::submit)
            .with(
                HandlerSignature.of(START_HANDLER, INVOKE_REQUEST_SERDE, CoreSerdes.VOID),
                (context, invokeRequest) -> {
                  this.internalStart(context, invokeRequest);
                  return null;
                });

    // Append shared methods
    for (var sharedMethod : sharedHandlers.values()) {
      workflowBuilder.with(
          HandlerSignature.of(
              sharedMethod.getHandlerSignature().getName(), INVOKE_REQUEST_SERDE, CoreSerdes.RAW),
          (context, invokeRequest) ->
              this.invokeSharedMethod(
                  sharedMethod.getHandlerSignature().getName(), context, invokeRequest));
    }

    // Prepare workflow manager service
    Service workflowManager =
        Service.virtualObject(workflowManagerObjectName(name))
            .with(
                HandlerSignature.of("getState", CoreSerdes.JSON_STRING, GET_STATE_RESPONSE_SERDE),
                this::getState)
            .with(
                HandlerSignature.of("setState", SET_STATE_REQUEST_SERDE, CoreSerdes.VOID),
                (context, setStateRequest) -> {
                  this.setState(context, setStateRequest);
                  return null;
                })
            .with(
                HandlerSignature.of("clearState", CoreSerdes.JSON_STRING, CoreSerdes.VOID),
                (context, s) -> {
                  this.clearState(context, s);
                  return null;
                })
            .with(
                HandlerSignature.of(
                    "waitDurablePromiseCompletion",
                    WAIT_DURABLE_PROMISE_COMPLETION_REQUEST_SERDE,
                    CoreSerdes.VOID),
                (context, waitDurablePromiseCompletionRequest) -> {
                  this.waitDurablePromiseCompletion(context, waitDurablePromiseCompletionRequest);
                  return null;
                })
            .with(
                HandlerSignature.of(
                    "getDurablePromiseCompletion",
                    CoreSerdes.JSON_STRING,
                    MAYBE_DURABLE_PROMISE_COMPLETION_SERDE),
                this::getDurablePromiseCompletion)
            .with(
                HandlerSignature.of(
                    "completeDurablePromise",
                    COMPLETE_DURABLE_PROMISE_REQUEST_SERDE,
                    CoreSerdes.VOID),
                (context, completeDurablePromiseRequest) -> {
                  this.completeDurablePromise(context, completeDurablePromiseRequest);
                  return null;
                })
            .with(
                HandlerSignature.of("tryStart", CoreSerdes.VOID, WORKFLOW_EXECUTION_STATE_SERDE),
                (context, unused) -> this.tryStart(context))
            .with(
                HandlerSignature.of("getOutput", CoreSerdes.VOID, GET_OUTPUT_RESPONSE_SERDE),
                (context, unused) -> this.getOutput(context))
            .with(
                HandlerSignature.of("setOutput", SET_OUTPUT_REQUEST_SERDE, CoreSerdes.VOID),
                (context, setOutputRequest) -> {
                  this.setOutput(context, setOutputRequest);
                  return null;
                })
            .with(
                HandlerSignature.of("cleanup", CoreSerdes.VOID, CoreSerdes.VOID),
                (context, unused) -> {
                  this.cleanup(context);
                  return null;
                })
            .build(options);

    return List.of(
        workflowBuilder.build(options).definitions().get(0), workflowManager.definitions().get(0));
  }
}
