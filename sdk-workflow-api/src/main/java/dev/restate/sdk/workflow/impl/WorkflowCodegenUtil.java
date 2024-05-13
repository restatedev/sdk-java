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

import dev.restate.sdk.Awaitable;
import dev.restate.sdk.Context;
import dev.restate.sdk.client.CallRequestOptions;
import dev.restate.sdk.client.IngressClient;
import dev.restate.sdk.common.*;
import dev.restate.sdk.workflow.WorkflowExecutionState;
import dev.restate.sdk.workflow.generated.GetOutputResponse;
import dev.restate.sdk.workflow.generated.GetStateResponse;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// Methods invoked from code-generated classes
public final class WorkflowCodegenUtil {

  private WorkflowCodegenUtil() {}

  // -- Restate client methods

  public static class RestateClient {
    private RestateClient() {}

    public static Awaitable<WorkflowExecutionState> submit(
        Context ctx, String workflowName, String workflowKey, @Nullable Object payload) {
      return ctx.call(
          Target.service(workflowName, "submit"),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          WorkflowImpl.WORKFLOW_EXECUTION_STATE_SERDE,
          InvokeRequest.fromAny(workflowKey, payload));
    }

    public static <T> Awaitable<Optional<T>> getOutput(
        Context ctx, String workflowName, String workflowKey, Serde<T> serde) {
      return ctx.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getOutput"),
              CoreSerdes.VOID,
              WorkflowImpl.GET_OUTPUT_RESPONSE_SERDE,
              null)
          .map(
              response -> {
                if (response.hasNotCompleted()) {
                  return Optional.empty();
                }
                if (response.hasFailure()) {
                  throw new TerminalException(
                      response.getFailure().getCode(), response.getFailure().getMessage());
                }
                return Optional.ofNullable(serde.deserialize(response.getValue()));
              });
    }

    public static Awaitable<Boolean> isCompleted(
        Context ctx, String workflowName, String workflowKey) {
      return ctx.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getOutput"),
              CoreSerdes.VOID,
              WorkflowImpl.GET_OUTPUT_RESPONSE_SERDE,
              null)
          .map(
              response -> {
                if (response.hasFailure()) {
                  throw new TerminalException(
                      response.getFailure().getCode(), response.getFailure().getMessage());
                }
                return !response.hasNotCompleted();
              });
    }

    public static <T> Awaitable<T> invokeShared(
        Context ctx,
        String workflowName,
        String handlerName,
        String workflowKey,
        @Nullable Object payload,
        Serde<T> resSerde) {
      return ctx.call(
          Target.service(workflowName, handlerName),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          resSerde,
          InvokeRequest.fromAny(workflowKey, payload));
    }

    public static void invokeSharedSend(
        Context ctx,
        String workflowName,
        String handlerName,
        String workflowKey,
        @Nullable Object payload) {
      ctx.send(
          Target.service(workflowName, handlerName),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          InvokeRequest.fromAny(workflowKey, payload));
    }

    public static void invokeSharedSendDelayed(
        Context ctx,
        String workflowName,
        String handlerName,
        String workflowKey,
        @Nullable Object payload,
        Duration delay) {
      ctx.send(
          Target.service(workflowName, handlerName),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          InvokeRequest.fromAny(workflowKey, payload),
          delay);
    }

    public static <T> Awaitable<Optional<T>> getState(
        Context ctx, String workflowName, String workflowKey, StateKey<T> key) {
      return ctx.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getState"),
              CoreSerdes.JSON_STRING,
              WorkflowImpl.GET_STATE_RESPONSE_SERDE,
              key.name())
          .map(
              response -> {
                if (response.hasEmpty()) {
                  return Optional.empty();
                }
                return Optional.of(key.serde().deserialize(response.getValue()));
              });
    }
  }

  // --- External client methods

  public static class ExternalClient {
    private ExternalClient() {}

    public static WorkflowExecutionState submit(
        IngressClient ingressClient,
        String workflowName,
        String workflowKey,
        @Nullable Object payload) {
      return ingressClient.call(
          Target.service(workflowName, "submit"),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          WorkflowImpl.WORKFLOW_EXECUTION_STATE_SERDE,
          InvokeRequest.fromAny(workflowKey, payload),
          CallRequestOptions.DEFAULT);
    }

    public static <T> Optional<T> getOutput(
        IngressClient ingressClient, String workflowName, String workflowKey, Serde<T> serde) {
      GetOutputResponse response =
          ingressClient.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getOutput"),
              CoreSerdes.VOID,
              WorkflowImpl.GET_OUTPUT_RESPONSE_SERDE,
              null,
              CallRequestOptions.DEFAULT);
      if (response.hasNotCompleted()) {
        return Optional.empty();
      }
      if (response.hasFailure()) {
        throw new TerminalException(
            response.getFailure().getCode(), response.getFailure().getMessage());
      }
      return Optional.ofNullable(serde.deserialize(response.getValue()));
    }

    public static boolean isCompleted(
        IngressClient ingressClient, String workflowName, String workflowKey) {
      GetOutputResponse response =
          ingressClient.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getOutput"),
              CoreSerdes.VOID,
              WorkflowImpl.GET_OUTPUT_RESPONSE_SERDE,
              null,
              CallRequestOptions.DEFAULT);
      if (response.hasFailure()) {
        throw new TerminalException(
            response.getFailure().getCode(), response.getFailure().getMessage());
      }
      return !response.hasNotCompleted();
    }

    public static <T> T invokeShared(
        IngressClient ingressClient,
        String workflowName,
        String handlerName,
        String workflowKey,
        @Nullable Object payload,
        Serde<T> resSerde) {
      return ingressClient.call(
          Target.service(workflowName, handlerName),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          resSerde,
          InvokeRequest.fromAny(workflowKey, payload),
          CallRequestOptions.DEFAULT);
    }

    public static void invokeSharedSend(
        IngressClient ingressClient,
        String workflowName,
        String handlerName,
        String workflowKey,
        @Nullable Object payload) {
      ingressClient.send(
          Target.service(workflowName, handlerName),
          WorkflowImpl.INVOKE_REQUEST_SERDE,
          InvokeRequest.fromAny(workflowKey, payload),
          null,
          CallRequestOptions.DEFAULT);
    }

    public static <T> Optional<T> getState(
        IngressClient ingressClient, String workflowName, String workflowKey, StateKey<T> key) {
      GetStateResponse response =
          ingressClient.call(
              Target.virtualObject(
                  workflowManagerObjectName(workflowName), workflowKey, "getState"),
              CoreSerdes.JSON_STRING,
              WorkflowImpl.GET_STATE_RESPONSE_SERDE,
              key.name(),
              CallRequestOptions.DEFAULT);
      if (response.hasEmpty()) {
        return Optional.empty();
      }
      return Optional.of(key.serde().deserialize(response.getValue()));
    }
  }
}
