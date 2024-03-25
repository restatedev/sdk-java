// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import static dev.restate.sdk.workflow.impl.WorkflowImpl.workflowManagerObjectName;

import dev.restate.sdk.*;
import dev.restate.sdk.common.*;
import dev.restate.sdk.common.function.ThrowingRunnable;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.workflow.DurablePromise;
import dev.restate.sdk.workflow.DurablePromiseHandle;
import dev.restate.sdk.workflow.DurablePromiseKey;
import dev.restate.sdk.workflow.WorkflowContext;
import dev.restate.sdk.workflow.generated.*;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

class WorkflowContextImpl implements WorkflowContext {

  private final Context ctx;
  private final String workflowFsqn;
  private final String workflowKey;
  private final boolean isExclusive;

  WorkflowContextImpl(Context ctx, String workflowFqsn, String workflowKey, boolean isExclusive) {
    this.ctx = ctx;
    this.workflowFsqn = workflowFqsn;
    this.workflowKey = workflowKey;
    this.isExclusive = isExclusive;
  }

  @Override
  public String workflowKey() {
    return this.workflowKey;
  }

  @Override
  public Request request() {
    return ctx.request();
  }

  // --- State ops

  @Override
  public <T> Optional<T> get(StateKey<T> key) {
    GetStateResponse response =
        this.ctx
            .call(
                workflowManagerTarget("getState"),
                CoreSerdes.JSON_STRING,
                WorkflowImpl.GET_STATE_RESPONSE_SERDE,
                key.name())
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
    this.ctx.send(workflowManagerTarget("clearState"), CoreSerdes.JSON_STRING, key.name());
  }

  @Override
  public <T> void set(StateKey<T> key, @NonNull T value) {
    if (!isExclusive) {
      throw new UnsupportedOperationException("Can't perform a state update on a SharedContext");
    }
    this.ctx.send(
        workflowManagerTarget("setState"),
        WorkflowImpl.SET_STATE_REQUEST_SERDE,
        SetStateRequest.newBuilder()
            .setStateKey(key.name())
            .setStateValue(key.serde().serializeToByteString(value))
            .build());
  }

  // -- Signal

  @Override
  public <T> DurablePromise<T> durablePromise(DurablePromiseKey<T> key) {
    Awakeable<T> awakeable = ctx.awakeable(key.serde());

    // Register durablePromise
    ctx.send(
        workflowManagerTarget("waitDurablePromiseCompletion"),
        WorkflowImpl.WAIT_DURABLE_PROMISE_COMPLETION_REQUEST_SERDE,
        WaitDurablePromiseCompletionRequest.newBuilder()
            .setDurablePromiseKey(key.name())
            .setAwakeableId(awakeable.id())
            .build());

    return new DurablePromise<>() {
      @Override
      public Awaitable<T> awaitable() {
        return awakeable;
      }

      @Override
      public Optional<T> peek() {
        MaybeDurablePromiseCompletion maybeDurablePromiseCompletion =
            ctx.call(
                    workflowManagerTarget("getDurablePromiseCompletion"),
                    CoreSerdes.JSON_STRING,
                    WorkflowImpl.MAYBE_DURABLE_PROMISE_COMPLETION_SERDE,
                    key.name())
                .await();

        switch (maybeDurablePromiseCompletion.getResultCase()) {
          case VALUE:
            return Optional.of(key.serde().deserialize(maybeDurablePromiseCompletion.getValue()));
          case FAILURE:
            throw new TerminalException(
                maybeDurablePromiseCompletion.getFailure().getCode(),
                maybeDurablePromiseCompletion.getFailure().getMessage());
          case NOT_COMPLETED:
            return Optional.empty();
        }
        throw new IllegalStateException("Unexpected response from WorkflowManager");
      }

      @Override
      public boolean isCompleted() {
        MaybeDurablePromiseCompletion maybeDurablePromiseCompletion =
            ctx.call(
                    workflowManagerTarget("getDurablePromiseCompletion"),
                    CoreSerdes.JSON_STRING,
                    WorkflowImpl.MAYBE_DURABLE_PROMISE_COMPLETION_SERDE,
                    key.name())
                .await();
        return !maybeDurablePromiseCompletion.hasNotCompleted();
      }
    };
  }

  @Override
  public <T> DurablePromiseHandle<T> durablePromiseHandle(DurablePromiseKey<T> key) {
    return new DurablePromiseHandle<>() {
      @Override
      public void resolve(T payload) throws IllegalStateException {
        ctx.send(
            workflowManagerTarget("completeDurablePromise"),
            WorkflowImpl.COMPLETE_DURABLE_PROMISE_REQUEST_SERDE,
            CompleteDurablePromiseRequest.newBuilder()
                .setDurablePromiseKey(key.name())
                .setCompletion(
                    DurablePromiseCompletion.newBuilder()
                        .setValue(key.serde().serializeToByteString(payload)))
                .build());
      }

      @Override
      public void reject(String reason) throws IllegalStateException {
        ctx.send(
            workflowManagerTarget("completeDurablePromise"),
            WorkflowImpl.COMPLETE_DURABLE_PROMISE_REQUEST_SERDE,
            CompleteDurablePromiseRequest.newBuilder()
                .setDurablePromiseKey(key.name())
                .setCompletion(
                    DurablePromiseCompletion.newBuilder()
                        .setFailure(Failure.newBuilder().setCode(2).setMessage(reason)))
                .build());
      }
    };
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
  public <T, R> Awaitable<R> call(
      Target target, Serde<T> inputSerde, Serde<R> outputSerde, T parameter) {
    return ctx.call(target, inputSerde, outputSerde, parameter);
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter) {
    ctx.send(target, inputSerde, parameter);
  }

  @Override
  public <T> void send(Target target, Serde<T> inputSerde, T parameter, Duration delay) {
    ctx.send(target, inputSerde, parameter, delay);
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

  private Target workflowManagerTarget(String handler) {
    return Target.virtualObject(
        workflowManagerObjectName(this.workflowFsqn), this.workflowKey, handler);
  }
}
