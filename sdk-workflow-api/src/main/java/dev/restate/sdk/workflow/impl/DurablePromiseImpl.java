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
import dev.restate.sdk.Awaitable;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.workflow.DurablePromise;
import dev.restate.sdk.workflow.DurablePromiseKey;
import dev.restate.sdk.workflow.generated.*;
import io.grpc.MethodDescriptor;
import java.util.Optional;

public final class DurablePromiseImpl<T> implements DurablePromise<T> {

  private final String workflowKey;
  private final ObjectContext ctx;
  private final DurablePromiseKey<T> key;
  private final Awaitable<T> awakeable;

  private final MethodDescriptor<GetDurablePromiseCompletionRequest, MaybeDurablePromiseCompletion>
      workflowManagerGetDurablePromiseCompletion;

  private DurablePromiseImpl(
      String workflowKey,
      ObjectContext ctx,
      DurablePromiseKey<T> key,
      Awaitable<T> awakeable,
      MethodDescriptor<GetDurablePromiseCompletionRequest, MaybeDurablePromiseCompletion>
          workflowManagerGetDurablePromiseCompletion) {
    this.workflowKey = workflowKey;
    this.ctx = ctx;
    this.key = key;
    this.awakeable = awakeable;
    this.workflowManagerGetDurablePromiseCompletion = workflowManagerGetDurablePromiseCompletion;
  }

  @Override
  public Awaitable<T> awaitable() {
    return awakeable;
  }

  @Override
  public Optional<T> peek() {
    MaybeDurablePromiseCompletion maybeDurablePromiseCompletion =
        this.ctx
            .call(
                workflowManagerGetDurablePromiseCompletion,
                GetDurablePromiseCompletionRequest.newBuilder()
                    .setKey(workflowKey)
                    .setDurablePromiseKey(key.name())
                    .build())
            .await();

    switch (maybeDurablePromiseCompletion.getResultCase()) {
      case VALUE:
        return Optional.of(key.serde().deserialize(maybeDurablePromiseCompletion.getValue()));
      case FAILURE:
        throw new TerminalException(
            TerminalException.Code.fromValue(maybeDurablePromiseCompletion.getFailure().getCode()),
            maybeDurablePromiseCompletion.getFailure().getMessage());
      case NOT_COMPLETED:
        return Optional.empty();
    }
    throw new IllegalStateException("Unexpected response from WorkflowManager");
  }

  @Override
  public boolean isCompleted() {
    MaybeDurablePromiseCompletion maybeDurablePromiseCompletion =
        this.ctx
            .call(
                workflowManagerGetDurablePromiseCompletion,
                GetDurablePromiseCompletionRequest.newBuilder()
                    .setKey(workflowKey)
                    .setDurablePromiseKey(key.name())
                    .build())
            .await();
    return !maybeDurablePromiseCompletion.hasNotCompleted();
  }

  static <T> DurablePromise<T> prepare(
      String workflowKey,
      ObjectContext ctx,
      DurablePromiseKey<T> key,
      MethodDescriptor<WaitDurablePromiseCompletionRequest, Empty>
          workflowManagerWaitDurablePromiseCompletion,
      MethodDescriptor<GetDurablePromiseCompletionRequest, MaybeDurablePromiseCompletion>
          workflowManagerGetDurablePromiseCompletion) {
    Awakeable<T> awakeable = ctx.awakeable(key.serde());

    // Register durablePromise
    ctx.oneWayCall(
        workflowManagerWaitDurablePromiseCompletion,
        WaitDurablePromiseCompletionRequest.newBuilder()
            .setKey(workflowKey)
            .setDurablePromiseKey(key.name())
            .setAwakeableId(awakeable.id())
            .build());

    return new DurablePromiseImpl<>(
        workflowKey, ctx, key, awakeable, workflowManagerGetDurablePromiseCompletion);
  }
}
