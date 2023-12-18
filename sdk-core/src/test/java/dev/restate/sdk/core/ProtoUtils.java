// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.StartMessage.StateEntry;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.core.testservices.GreetingRequest;
import dev.restate.sdk.core.testservices.GreetingResponse;
import io.grpc.MethodDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProtoUtils {

  /**
   * Variant of {@link MessageHeader#fromMessage(MessageLite)} supporting StartMessage and
   * CompletionMessage.
   */
  public static MessageHeader headerFromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) {
      return new MessageHeader(MessageType.StartMessage, (short) 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, (short) 0, msg.getSerializedSize());
    }
    return MessageHeader.fromMessage(msg);
  }

  public static Protocol.StartMessage.Builder startMessage(int entries) {
    return Protocol.StartMessage.newBuilder()
        .setId(ByteString.copyFromUtf8("abc"))
        .setDebugId("abc")
        .setKnownEntries(entries)
        .setPartialState(true);
  }

  @SafeVarargs
  public static Protocol.StartMessage.Builder startMessage(
      int entries, Map.Entry<String, String>... stateEntries) {
    return startMessage(entries)
        .addAllStateMap(
            Arrays.stream(stateEntries)
                .map(
                    e ->
                        StateEntry.newBuilder()
                            .setKey(ByteString.copyFromUtf8(e.getKey()))
                            .setValue(ByteString.copyFromUtf8(e.getValue()))
                            .build())
                .collect(Collectors.toList()));
  }

  public static Protocol.CompletionMessage completionMessage(int index, String value) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setValue(ByteString.copyFromUtf8(value))
        .build();
  }

  public static Protocol.CompletionMessage completionMessage(
      int index, MessageLiteOrBuilder value) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setValue(build(value).toByteString())
        .build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, Throwable e) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setFailure(Util.toProtocolFailure(e))
        .build();
  }

  public static Protocol.EntryAckMessage ackMessage(int index) {
    return Protocol.EntryAckMessage.newBuilder().setEntryIndex(index).build();
  }

  public static Protocol.SuspensionMessage suspensionMessage(Integer... indexes) {
    return Protocol.SuspensionMessage.newBuilder().addAllEntryIndexes(List.of(indexes)).build();
  }

  public static Protocol.PollInputStreamEntryMessage inputMessage(MessageLiteOrBuilder value) {
    return Protocol.PollInputStreamEntryMessage.newBuilder()
        .setValue(build(value).toByteString())
        .build();
  }

  public static Protocol.PollInputStreamEntryMessage inputMessage(Throwable error) {
    return Protocol.PollInputStreamEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(error))
        .build();
  }

  public static Protocol.OutputStreamEntryMessage outputMessage(MessageLiteOrBuilder value) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setValue(build(value).toByteString())
        .build();
  }

  public static Protocol.OutputStreamEntryMessage outputMessage(
      TerminalException.Code code, String message) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(code, message))
        .build();
  }

  public static Protocol.OutputStreamEntryMessage outputMessage(Throwable e) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(e))
        .build();
  }

  public static Protocol.GetStateEntryMessage.Builder getStateMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(key));
  }

  public static Protocol.GetStateEntryMessage.Builder getStateMessage(String key, Throwable error) {
    return getStateMessage(key).setFailure(Util.toProtocolFailure(error));
  }

  public static Protocol.GetStateEntryMessage getStateEmptyMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setEmpty(Empty.getDefaultInstance())
        .build();
  }

  public static Protocol.GetStateEntryMessage getStateMessage(String key, String value) {
    return getStateMessage(key).setValue(ByteString.copyFromUtf8(value)).build();
  }

  public static Protocol.SetStateEntryMessage setStateMessage(String key, String value) {
    return Protocol.SetStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(ByteString.copyFromUtf8(value))
        .build();
  }

  public static Protocol.ClearStateEntryMessage clearStateMessage(String key) {
    return Protocol.ClearStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .build();
  }

  public static <T extends MessageLite, R extends MessageLite>
      Protocol.InvokeEntryMessage.Builder invokeMessage(
          MethodDescriptor<T, R> methodDescriptor, T parameter) {
    return Protocol.InvokeEntryMessage.newBuilder()
        .setServiceName(methodDescriptor.getServiceName())
        .setMethodName(methodDescriptor.getBareMethodName())
        .setParameter(parameter.toByteString());
  }

  public static <T extends MessageLite, R extends MessageLite>
      Protocol.InvokeEntryMessage invokeMessage(
          MethodDescriptor<T, R> methodDescriptor, T parameter, R result) {
    return invokeMessage(methodDescriptor, parameter).setValue(result.toByteString()).build();
  }

  public static <T extends MessageLite, R extends MessageLite>
      Protocol.InvokeEntryMessage invokeMessage(
          MethodDescriptor<T, R> methodDescriptor, T parameter, Throwable e) {
    return invokeMessage(methodDescriptor, parameter).setFailure(Util.toProtocolFailure(e)).build();
  }

  public static <T extends MessageLite>
      Protocol.BackgroundInvokeEntryMessage.Builder backgroundInvokeMessage(
          MethodDescriptor<T, ?> methodDescriptor, T parameter) {
    return Protocol.BackgroundInvokeEntryMessage.newBuilder()
        .setServiceName(methodDescriptor.getServiceName())
        .setMethodName(methodDescriptor.getBareMethodName())
        .setParameter(parameter.toByteString());
  }

  public static Protocol.AwakeableEntryMessage.Builder awakeable() {
    return Protocol.AwakeableEntryMessage.newBuilder();
  }

  public static Protocol.AwakeableEntryMessage awakeable(String value) {
    return awakeable().setValue(ByteString.copyFromUtf8(value)).build();
  }

  public static GreetingRequest greetingRequest(String name) {
    return GreetingRequest.newBuilder().setName(name).build();
  }

  public static GreetingResponse greetingResponse(String message) {
    return GreetingResponse.newBuilder().setMessage(message).build();
  }

  public static Java.CombinatorAwaitableEntryMessage combinatorsMessage(Integer... order) {
    return Java.CombinatorAwaitableEntryMessage.newBuilder()
        .addAllEntryIndex(Arrays.asList(order))
        .build();
  }

  static MessageLite build(MessageLiteOrBuilder value) {
    if (value instanceof MessageLite) {
      return (MessageLite) value;
    } else {
      return ((MessageLite.Builder) value).build();
    }
  }
}
