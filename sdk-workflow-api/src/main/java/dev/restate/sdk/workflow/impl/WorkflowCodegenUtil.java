// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.workflow.impl;

import static io.grpc.stub.ClientCalls.blockingUnaryCall;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.restate.generated.IngressGrpc;
import dev.restate.sdk.Awaitable;
import dev.restate.sdk.Context;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.workflow.generated.*;
import dev.restate.sdk.workflow.template.generated.WorkflowGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

// Methods invoked from code-generated classes
public class WorkflowCodegenUtil {

  private WorkflowCodegenUtil() {}

  public static <T> T valueToT(Serde<T> serde, Value value) {
    String reqAsString;
    try {
      reqAsString = JsonFormat.printer().print(value);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    return serde.deserialize(reqAsString.getBytes(StandardCharsets.UTF_8));
  }

  public static <T> Value tToValue(Serde<T> serde, T value) {
    String resAsString = serde.serializeToByteString(value).toStringUtf8();
    Value.Builder outValueBuilder = Value.newBuilder();
    try {
      JsonFormat.parser().merge(resAsString, outValueBuilder);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    return outValueBuilder.build();
  }

  // -- Restate client methods

  public static class RestateClient {
    private RestateClient() {}

    public static Awaitable<WorkflowExecutionState> submit(
        Context ctx,
        MethodDescriptor<InvokeRequest, SubmitResponse> submitMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }
      return ctx.call(submitMethodDesc, reqBuilder.build()).map(SubmitResponse::getState);
    }

    public static <T> Awaitable<Optional<T>> getOutput(
        Context ctx,
        MethodDescriptor<OutputRequest, GetOutputResponse> getOutputMethodDesc,
        String workflowKey,
        Serde<T> serde) {
      return ctx.call(getOutputMethodDesc, OutputRequest.newBuilder().setKey(workflowKey).build())
          .map(
              response -> {
                if (response.hasNotCompleted()) {
                  return Optional.empty();
                }
                if (response.hasFailure()) {
                  throw new TerminalException(
                      TerminalException.Code.fromValue(response.getFailure().getCode()),
                      response.getFailure().getMessage());
                }
                return Optional.ofNullable(valueToT(serde, response.getValue()));
              });
    }

    public static Awaitable<Boolean> isCompleted(
        Context ctx,
        MethodDescriptor<OutputRequest, GetOutputResponse> getOutputMethodDesc,
        String workflowKey) {
      return ctx.call(getOutputMethodDesc, OutputRequest.newBuilder().setKey(workflowKey).build())
          .map(
              response -> {
                if (response.hasFailure()) {
                  throw new TerminalException(
                      TerminalException.Code.fromValue(response.getFailure().getCode()),
                      response.getFailure().getMessage());
                }
                return !response.hasNotCompleted();
              });
    }

    public static Awaitable<Value> invokeShared(
        Context ctx,
        MethodDescriptor<InvokeRequest, Value> invokeMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      return ctx.call(invokeMethodDesc, reqBuilder.build());
    }

    public static void invokeSharedOneWay(
        Context ctx,
        MethodDescriptor<InvokeRequest, Value> invokeMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      ctx.oneWayCall(invokeMethodDesc, reqBuilder.build());
    }

    public static void invokeSharedDelayed(
        Context ctx,
        MethodDescriptor<InvokeRequest, Value> invokeMethodDesc,
        String workflowKey,
        @Nullable Value payload,
        Duration delay) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      ctx.delayedCall(invokeMethodDesc, reqBuilder.build(), delay);
    }

    public static <T> Awaitable<Optional<T>> getState(
        Context ctx,
        MethodDescriptor<StateRequest, GetStateResponse> getStateMethodDesc,
        String workflowKey,
        StateKey<T> key) {
      return ctx.call(
              getStateMethodDesc,
              StateRequest.newBuilder().setStateKey(key.name()).setKey(workflowKey).build())
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
        Channel channel,
        MethodDescriptor<InvokeRequest, SubmitResponse> submitMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      return blockingUnaryCall(channel, submitMethodDesc, CallOptions.DEFAULT, reqBuilder.build())
          .getState();
    }

    public static <T> Optional<T> getOutput(
        Channel channel,
        MethodDescriptor<OutputRequest, GetOutputResponse> getOutputMethodDesc,
        String workflowKey,
        Serde<T> serde) {
      GetOutputResponse response =
          blockingUnaryCall(
              channel,
              getOutputMethodDesc,
              CallOptions.DEFAULT,
              OutputRequest.newBuilder().setKey(workflowKey).build());
      if (response.hasNotCompleted()) {
        return Optional.empty();
      }
      if (response.hasFailure()) {
        throw new TerminalException(
            TerminalException.Code.fromValue(response.getFailure().getCode()),
            response.getFailure().getMessage());
      }
      return Optional.ofNullable(valueToT(serde, response.getValue()));
    }

    public static boolean isCompleted(
        Channel channel,
        MethodDescriptor<OutputRequest, GetOutputResponse> getOutputMethodDesc,
        String workflowKey) {
      GetOutputResponse response =
          blockingUnaryCall(
              channel,
              getOutputMethodDesc,
              CallOptions.DEFAULT,
              OutputRequest.newBuilder().setKey(workflowKey).build());
      if (response.hasFailure()) {
        throw new TerminalException(
            TerminalException.Code.fromValue(response.getFailure().getCode()),
            response.getFailure().getMessage());
      }
      return !response.hasNotCompleted();
    }

    public static Value invokeShared(
        Channel channel,
        MethodDescriptor<InvokeRequest, Value> invokeMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      return blockingUnaryCall(channel, invokeMethodDesc, CallOptions.DEFAULT, reqBuilder.build());
    }

    public static void invokeSharedOneWay(
        Channel channel,
        MethodDescriptor<InvokeRequest, Value> invokeMethodDesc,
        String workflowKey,
        @Nullable Value payload) {
      InvokeRequest.Builder reqBuilder = InvokeRequest.newBuilder().setKey(workflowKey);
      if (payload != null) {
        reqBuilder.setPayload(payload);
      }

      var ingressClient = IngressGrpc.newBlockingStub(channel);
      ingressClient.invoke(
          dev.restate.generated.InvokeRequest.newBuilder()
              .setService(invokeMethodDesc.getServiceName())
              .setMethod(invokeMethodDesc.getBareMethodName())
              .setPb(reqBuilder.build().toByteString())
              .build());
    }

    public static <T> Optional<T> getState(
        Channel channel,
        MethodDescriptor<StateRequest, GetStateResponse> getStateMethodDesc,
        String workflowKey,
        StateKey<T> key) {
      GetStateResponse response =
          blockingUnaryCall(
              channel,
              getStateMethodDesc,
              CallOptions.DEFAULT,
              StateRequest.newBuilder().setStateKey(key.name()).setKey(workflowKey).build());
      if (response.hasEmpty()) {
        return Optional.empty();
      }
      return Optional.of(key.serde().deserialize(response.getValue()));
    }
  }

  // --- Method descriptors manglers

  public static <Req, Res> MethodDescriptor<Req, Res> generateMethodDescriptorForWorkflowManager(
      MethodDescriptor<Req, Res> original, String workflowFqsn) {
    String workflowServiceFqsn = workflowFqsn + WorkflowMangledDescriptors.MANAGER_SERVICE_SUFFIX;
    return original.toBuilder()
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                workflowServiceFqsn, Objects.requireNonNull(original.getBareMethodName())))
        .build();
  }

  public static <Req, Res> MethodDescriptor<Req, Res> generateMethodDescriptorForWorkflow(
      MethodDescriptor<Req, Res> original, String workflowFqsn, String methodName) {
    return original.toBuilder()
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                workflowFqsn, DescriptorUtils.toMethodName(methodName)))
        .build();
  }

  public static MethodDescriptor<InvokeRequest, SubmitResponse>
      generateMethodDescriptorForWorkflowSubmit(String workflowFqsn) {
    return WorkflowGrpc.getSubmitMethod().toBuilder()
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                workflowFqsn, WorkflowGrpc.getSubmitMethod().getBareMethodName()))
        .build();
  }

  public static MethodDescriptor<InvokeRequest, Empty>
      generateMethodDescriptorForWorkflowInternalStart(String workflowFqsn) {
    return WorkflowGrpc.getInternalStartMethod().toBuilder()
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                workflowFqsn, WorkflowGrpc.getInternalStartMethod().getBareMethodName()))
        .build();
  }
}
