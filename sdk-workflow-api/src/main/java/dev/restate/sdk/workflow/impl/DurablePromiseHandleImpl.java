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
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.workflow.DurablePromiseHandle;
import dev.restate.sdk.workflow.DurablePromiseKey;
import dev.restate.sdk.workflow.generated.CompleteDurablePromiseRequest;
import dev.restate.sdk.workflow.generated.DurablePromiseCompletion;
import dev.restate.sdk.workflow.generated.Failure;
import io.grpc.MethodDescriptor;

/** This class represents a handle to an {@link DurablePromiseImpl} created in another service. */
public final class DurablePromiseHandleImpl<T> implements DurablePromiseHandle<T> {
  private final String workflowKey;
  private final MethodDescriptor<CompleteDurablePromiseRequest, Empty>
      workflowManagerCompleteDurablePromise;
  private final ObjectContext ctx;
  private final DurablePromiseKey<T> key;

  DurablePromiseHandleImpl(
      String workflowKey,
      ObjectContext ctx,
      MethodDescriptor<CompleteDurablePromiseRequest, Empty> workflowManagerCompleteDurablePromise,
      DurablePromiseKey<T> key) {
    this.workflowKey = workflowKey;
    this.ctx = ctx;
    this.workflowManagerCompleteDurablePromise = workflowManagerCompleteDurablePromise;
    this.key = key;
  }

  /**
   * @throws IllegalStateException if the signal cannot be completed
   */
  @Override
  public void resolve(T payload) throws IllegalStateException {
    this.ctx.oneWayCall(
        this.workflowManagerCompleteDurablePromise,
        CompleteDurablePromiseRequest.newBuilder()
            .setKey(this.workflowKey)
            .setDurablePromiseKey(key.name())
            .setCompletion(
                DurablePromiseCompletion.newBuilder()
                    .setValue(this.key.serde().serializeToByteString(payload)))
            .build());
  }

  /**
   * @throws IllegalStateException if the signal cannot be completed
   */
  @Override
  public void reject(String reason) throws IllegalStateException {
    this.ctx.oneWayCall(
        this.workflowManagerCompleteDurablePromise,
        CompleteDurablePromiseRequest.newBuilder()
            .setKey(this.workflowKey)
            .setDurablePromiseKey(key.name())
            .setCompletion(
                DurablePromiseCompletion.newBuilder()
                    .setFailure(Failure.newBuilder().setCode(2).setMessage(reason)))
            .build());
  }
}
