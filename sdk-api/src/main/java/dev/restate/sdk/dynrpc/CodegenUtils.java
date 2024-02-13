// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.dynrpc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.restate.generated.IngressGrpc;
import dev.restate.sdk.Awaitable;
import dev.restate.sdk.Context;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.dynrpc.generated.KeyedRpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcRequest;
import dev.restate.sdk.dynrpc.generated.RpcResponse;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.grpc.stub.ClientCalls.blockingUnaryCall;

// Methods invoked from code-generated classes
// DON'T invoke these methods directly unless you know what you're doing!
public class CodegenUtils {

  private CodegenUtils() {}

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

    public static Awaitable<Value> invoke(
            Context ctx,
            MethodDescriptor<RpcRequest, RpcResponse> methodDesc,
            @Nullable Value payload) {
      RpcRequest.Builder reqBuilder = RpcRequest.newBuilder();
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      return ctx.call(methodDesc, reqBuilder.build()).map(RpcResponse::getResponse);
    }

    public static void invokeOneWay(
            Context ctx,
            MethodDescriptor<RpcRequest, RpcResponse> methodDesc,
            @Nullable Value payload) {
      RpcRequest.Builder reqBuilder = RpcRequest.newBuilder();
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      ctx.oneWayCall(methodDesc, reqBuilder.build());
    }

    public static void invokeDelayed(
            Context ctx,
            MethodDescriptor<RpcRequest, RpcResponse> methodDesc,
            String key,
            @Nullable Value payload,
            Duration delay) {
      RpcRequest.Builder reqBuilder = RpcRequest.newBuilder();
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      ctx.delayedCall(methodDesc, reqBuilder.build(), delay);
    }

    public static Awaitable<Value> invokeKeyed(
        Context ctx,
        MethodDescriptor<KeyedRpcRequest, RpcResponse> methodDesc,
        String key,
        @Nullable Value payload) {
      KeyedRpcRequest.Builder reqBuilder = KeyedRpcRequest.newBuilder().setKey(key);
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      return ctx.call(methodDesc, reqBuilder.build()).map(RpcResponse::getResponse);
    }

    public static void invokeKeyedOneWay(
        Context ctx,
        MethodDescriptor<KeyedRpcRequest, RpcResponse> methodDesc,
        String key,
        @Nullable Value payload) {
      KeyedRpcRequest.Builder reqBuilder = KeyedRpcRequest.newBuilder().setKey(key);
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      ctx.oneWayCall(methodDesc, reqBuilder.build());
    }

    public static void invokeKeyedDelayed(
        Context ctx,
        MethodDescriptor<KeyedRpcRequest, RpcResponse> methodDesc,
        String key,
        @Nullable Value payload,
        Duration delay) {
      KeyedRpcRequest.Builder reqBuilder = KeyedRpcRequest.newBuilder().setKey(key);
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      ctx.delayedCall(methodDesc, reqBuilder.build(), delay);
    }

  }

  // --- External client methods

  public static class ExternalClient {
    private ExternalClient() {}

    public static RpcResponse invoke(
            Channel channel,
            MethodDescriptor<RpcRequest, RpcResponse> methodDesc,
            @Nullable Value payload) {
      RpcRequest.Builder reqBuilder = RpcRequest.newBuilder();
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      return blockingUnaryCall(channel, methodDesc, CallOptions.DEFAULT, reqBuilder.build());
    }

    public static void invokeOneWay(
            Channel channel,
            MethodDescriptor<RpcRequest, RpcResponse> methodDesc,
            @Nullable Value payload) {
      RpcRequest.Builder reqBuilder = RpcRequest.newBuilder();
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      var ingressClient = IngressGrpc.newBlockingStub(channel);
      ingressClient.invoke(
              dev.restate.generated.InvokeRequest.newBuilder()
                      .setService(methodDesc.getServiceName())
                      .setMethod(methodDesc.getBareMethodName())
                      .setPb(reqBuilder.build().toByteString())
                      .build());
    }

    public static RpcResponse invokeKeyed(
            Channel channel,
            MethodDescriptor<KeyedRpcRequest, RpcResponse> methodDesc,
            String key,
            @Nullable Value payload) {
      KeyedRpcRequest.Builder reqBuilder = KeyedRpcRequest.newBuilder().setKey(key);
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      return blockingUnaryCall(channel, methodDesc, CallOptions.DEFAULT, reqBuilder.build());
    }

    public static void invokeKeyedOneWay(
            Channel channel,
            MethodDescriptor<KeyedRpcRequest, RpcResponse> methodDesc,
            String key,
            @Nullable Value payload) {
      KeyedRpcRequest.Builder reqBuilder = KeyedRpcRequest.newBuilder().setKey(key);
      if (payload != null) {
        reqBuilder.setRequest(payload);
      }

      var ingressClient = IngressGrpc.newBlockingStub(channel);
      ingressClient.invoke(
              dev.restate.generated.InvokeRequest.newBuilder()
                      .setService(methodDesc.getServiceName())
                      .setMethod(methodDesc.getBareMethodName())
                      .setPb(reqBuilder.build().toByteString())
                      .build());
    }
  }

  // --- Method descriptors manglers

  public static <Req, Res> MethodDescriptor<Req, Res> generateMethodDescriptor(
      MethodDescriptor<Req, Res> original, String serviceFqn, String methodName) {
    return original.toBuilder()
        .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceFqn, methodName))
        .build();
  }
}
