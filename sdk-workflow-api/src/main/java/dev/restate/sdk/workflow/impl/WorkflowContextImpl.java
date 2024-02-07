// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import com.google.protobuf.Empty;
import dev.restate.sdk.*;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.common.function.ThrowingRunnable;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.workflow.*;
import dev.restate.sdk.workflow.generated.*;
import dev.restate.sdk.workflow.template.generated.WorkflowManagerGrpc;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.Optional;
import javax.annotation.Nonnull;

class WorkflowContextImpl implements WorkflowContext {

  private final KeyedContext ctx;
  private final String workflowKey;
  private final boolean isExclusive;

  private final MethodDescriptor<StateRequest, GetStateResponse> workflowManagerGetState;
  private final MethodDescriptor<SetStateRequest, Empty> workflowManagerSetState;
  private final MethodDescriptor<StateRequest, Empty> workflowManagerClearState;
  private final MethodDescriptor<WaitDurablePromiseCompletionRequest, Empty>
      workflowManagerWaitDurablePromiseCompletion;
  private final MethodDescriptor<GetDurablePromiseCompletionRequest, MaybeDurablePromiseCompletion>
      workflowManagerGetDurablePromiseCompletion;
  private final MethodDescriptor<CompleteDurablePromiseRequest, Empty>
      workflowManagerCompleteSignal;

  WorkflowContextImpl(
      String workflowFqsn, KeyedContext ctx, String workflowKey, boolean isExclusive) {
    this.ctx = ctx;
    this.workflowKey = workflowKey;
    this.isExclusive = isExclusive;

    // Descriptors for methods we invoke
    this.workflowManagerGetState =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getGetStateMethod(), workflowFqsn);
    this.workflowManagerSetState =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getSetStateMethod(), workflowFqsn);
    this.workflowManagerClearState =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getClearStateMethod(), workflowFqsn);
    this.workflowManagerWaitDurablePromiseCompletion =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getWaitDurablePromiseCompletionMethod(), workflowFqsn);
    this.workflowManagerGetDurablePromiseCompletion =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getGetDurablePromiseCompletionMethod(), workflowFqsn);
    this.workflowManagerCompleteSignal =
        WorkflowCodegenUtil.generateMethodDescriptorForWorkflowManager(
            WorkflowManagerGrpc.getCompleteDurablePromiseMethod(), workflowFqsn);
  }

  @Override
  public String workflowKey() {
    return this.workflowKey;
  }

  // --- State ops

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    GetStateResponse response =
        this.ctx
            .call(
                this.workflowManagerGetState,
                StateRequest.newBuilder().setKey(this.workflowKey).setStateKey(key.name()).build())
            .await();

    switch (response.getResultCase()) {
      case VALUE:
        return Optional.of(key.serde().deserialize(response.getValue()));
      case EMPTY:
        return Optional.empty();
    }
    throw new IllegalStateException("Unexpected response from WorkflowManager");
  }

  @Override
  public void clear(StateKey<?> key) {
    if (!isExclusive) {
      throw new UnsupportedOperationException("Can't perform a state update on a SharedContext");
    }
    this.ctx.oneWayCall(
        this.workflowManagerClearState,
        StateRequest.newBuilder().setKey(this.workflowKey).setStateKey(key.name()).build());
  }

  @Override
  public <T> void set(StateKey<T> key, @Nonnull T value) {
    if (!isExclusive) {
      throw new UnsupportedOperationException("Can't perform a state update on a SharedContext");
    }
    this.ctx.oneWayCall(
        this.workflowManagerSetState,
        SetStateRequest.newBuilder()
            .setKey(this.workflowKey)
            .setStateKey(key.name())
            .setStateValue(key.serde().serializeToByteString(value))
            .build());
  }

  // -- Signal

  @Override
  public <T> DurablePromise<T> durablePromise(DurablePromiseKey<T> key) {
    return DurablePromiseImpl.prepare(
        workflowKey,
        ctx,
        key,
        workflowManagerWaitDurablePromiseCompletion,
        workflowManagerGetDurablePromiseCompletion);
  }

  @Override
  public <T> DurablePromiseHandle<T> durablePromiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandleImpl<>(
        workflowKey, ctx, this.workflowManagerCompleteSignal, key);
  }

  // -- Delegates to RestateContext

  @Override
  public void sleep(Duration duration) {
    ctx.sleep(duration);
  }

  @Override
  public Awaitable<Void> timer(Duration duration) {
    return ctx.timer(duration);
  }

  @Override
  public <T, R> Awaitable<R> call(MethodDescriptor<T, R> methodDescriptor, T parameter) {
    return ctx.call(methodDescriptor, parameter);
  }

  @Override
  public <T> void oneWayCall(MethodDescriptor<T, ?> methodDescriptor, T parameter) {
    ctx.oneWayCall(methodDescriptor, parameter);
  }

  @Override
  public <T> void delayedCall(
      MethodDescriptor<T, ?> methodDescriptor, T parameter, Duration delay) {
    ctx.delayedCall(methodDescriptor, parameter, delay);
  }

  @Override
  public <T> T sideEffect(Serde<T> serde, ThrowingSupplier<T> action) throws TerminalException {
    return ctx.sideEffect(serde, action);
  }

  @Override
  public void sideEffect(ThrowingRunnable runnable) throws TerminalException {
    ctx.sideEffect(runnable);
  }

  @Override
  public <T> Awakeable<T> awakeable(Serde<T> serde) {
    return ctx.awakeable(serde);
  }

  @Override
  public AwakeableHandle awakeableHandle(String id) {
    return ctx.awakeableHandle(id);
  }

  @Override
  public RestateRandom random() {
    return ctx.random();
  }
}
