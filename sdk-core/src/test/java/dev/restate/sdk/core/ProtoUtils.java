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
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.discovery.Discovery;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.StartMessage.StateEntry;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.Target;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProtoUtils {

  public static String serviceProtocolContentTypeHeader() {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(Protocol.ServiceProtocolVersion.V1);
  }

  public static String serviceProtocolContentTypeHeader(boolean enableContextPreview) {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        ServiceProtocol.maxServiceProtocolVersion(enableContextPreview));
  }

  public static String serviceProtocolDiscoveryContentTypeHeader() {
    return ServiceProtocol.serviceDiscoveryProtocolVersionToHeaderValue(
        Discovery.ServiceDiscoveryProtocolVersion.V1);
  }

  /**
   * Variant of {@link MessageHeader#fromMessage(MessageLite)} supporting StartMessage and
   * CompletionMessage.
   */
  public static MessageHeader headerFromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) {
      return new MessageHeader(MessageType.StartMessage, 0, msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, (short) 0, msg.getSerializedSize());
    }
    return MessageHeader.fromMessage(msg);
  }

  public static ByteBuffer invocationInputToByteString(InvocationInput invocationInput) {
    ByteBuffer buffer = ByteBuffer.allocate(MessageEncoder.encodeLength(invocationInput.message()));

    buffer.putLong(invocationInput.header().encode());
    buffer.put(invocationInput.message().toByteString().asReadOnlyByteBuffer());

    buffer.flip();
    return buffer;
  }

  public static ByteBuffer messageToByteString(MessageLiteOrBuilder msgOrBuilder) {
    var msg = build(msgOrBuilder);
    return invocationInputToByteString(InvocationInput.of(headerFromMessage(msg), msg));
  }

  public static List<MessageLite> bufferToMessages(List<ByteBuffer> byteBuffers) {
    AssertSubscriber<InvocationInput> subscriber = AssertSubscriber.create(Long.MAX_VALUE);
    Multi.createFrom().iterable(byteBuffers).subscribe(new MessageDecoder(subscriber));
    subscriber.awaitCompletion();
    return subscriber.getItems().stream()
        .map(InvocationInput::message)
        .collect(Collectors.toList());
  }

  public static Protocol.StartMessage.Builder startMessage(int entries) {
    return Protocol.StartMessage.newBuilder()
        .setId(ByteString.copyFromUtf8("abc"))
        .setDebugId("abc")
        .setKnownEntries(entries)
        .setPartialState(true);
  }

  public static Protocol.StartMessage.Builder startMessage(int entries, String key) {
    return Protocol.StartMessage.newBuilder()
        .setId(ByteString.copyFromUtf8("abc"))
        .setDebugId("abc")
        .setKnownEntries(entries)
        .setKey(key)
        .setPartialState(true);
  }

  @SafeVarargs
  public static Protocol.StartMessage.Builder startMessage(
      int entries, String key, Map.Entry<String, String>... stateEntries) {
    return startMessage(entries, key)
        .addAllStateMap(
            Arrays.stream(stateEntries)
                .map(
                    e ->
                        StateEntry.newBuilder()
                            .setKey(ByteString.copyFromUtf8(e.getKey()))
                            .setValue(
                                ByteString.copyFrom(TestSerdes.STRING.serialize(e.getValue())))
                            .build())
                .collect(Collectors.toList()));
  }

  public static Protocol.CompletionMessage.Builder completionMessage(int index) {
    return Protocol.CompletionMessage.newBuilder().setEntryIndex(index);
  }

  public static <T> Protocol.CompletionMessage completionMessage(
      int index, Serde<T> serde, T value) {
    return completionMessage(index).setValue(ByteString.copyFrom(serde.serialize(value))).build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, String value) {
    return completionMessage(index, TestSerdes.STRING, value);
  }

  public static Protocol.CompletionMessage completionMessage(
      int index, MessageLiteOrBuilder value) {
    return completionMessage(index).setValue(build(value).toByteString()).build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, Throwable e) {
    return completionMessage(index).setFailure(Util.toProtocolFailure(e)).build();
  }

  public static Protocol.EntryAckMessage ackMessage(int index) {
    return Protocol.EntryAckMessage.newBuilder().setEntryIndex(index).build();
  }

  public static Protocol.SuspensionMessage suspensionMessage(Integer... indexes) {
    return Protocol.SuspensionMessage.newBuilder().addAllEntryIndexes(List.of(indexes)).build();
  }

  public static Protocol.InputEntryMessage inputMessage() {
    return Protocol.InputEntryMessage.newBuilder().setValue(ByteString.EMPTY).build();
  }

  public static Protocol.InputEntryMessage inputMessage(byte[] value) {
    return Protocol.InputEntryMessage.newBuilder().setValue(ByteString.copyFrom(value)).build();
  }

  public static <T> Protocol.InputEntryMessage inputMessage(Serde<T> serde, T value) {
    return Protocol.InputEntryMessage.newBuilder()
        .setValue(ByteString.copyFrom(serde.serialize(value)))
        .build();
  }

  public static Protocol.InputEntryMessage inputMessage(String value) {
    return inputMessage(TestSerdes.STRING, value);
  }

  public static Protocol.InputEntryMessage inputMessage(int value) {
    return inputMessage(TestSerdes.INT, value);
  }

  public static <T> Protocol.OutputEntryMessage outputMessage(Serde<T> serde, T value) {
    return Protocol.OutputEntryMessage.newBuilder()
        .setValue(ByteString.copyFrom(serde.serialize(value)))
        .build();
  }

  public static Protocol.OutputEntryMessage outputMessage(String value) {
    return outputMessage(TestSerdes.STRING, value);
  }

  public static Protocol.OutputEntryMessage outputMessage(int value) {
    return outputMessage(TestSerdes.INT, value);
  }

  public static Protocol.OutputEntryMessage outputMessage(byte[] b) {
    return outputMessage(Serde.RAW, b);
  }

  public static Protocol.OutputEntryMessage outputMessage() {
    return Protocol.OutputEntryMessage.newBuilder().setValue(ByteString.EMPTY).build();
  }

  public static Protocol.OutputEntryMessage outputMessage(int code, String message) {
    return Protocol.OutputEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(code, message))
        .build();
  }

  public static Protocol.OutputEntryMessage outputMessage(Throwable e) {
    return Protocol.OutputEntryMessage.newBuilder().setFailure(Util.toProtocolFailure(e)).build();
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
        .setEmpty(Protocol.Empty.getDefaultInstance())
        .build();
  }

  public static <T> Protocol.GetStateEntryMessage getStateMessage(
      String key, Serde<T> serde, T value) {
    return getStateMessage(key).setValue(ByteString.copyFrom(serde.serialize(value))).build();
  }

  public static Protocol.GetStateEntryMessage getStateMessage(String key, String value) {
    return getStateMessage(key, TestSerdes.STRING, value);
  }

  public static <T> Protocol.SetStateEntryMessage setStateMessage(
      String key, Serde<T> serde, T value) {
    return Protocol.SetStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(ByteString.copyFrom(serde.serialize(value)))
        .build();
  }

  public static Protocol.SetStateEntryMessage setStateMessage(String key, String value) {
    return setStateMessage(key, TestSerdes.STRING, value);
  }

  public static Protocol.ClearStateEntryMessage clearStateMessage(String key) {
    return Protocol.ClearStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .build();
  }

  public static Protocol.CallEntryMessage.Builder invokeMessage(Target target) {
    Protocol.CallEntryMessage.Builder builder =
        Protocol.CallEntryMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler());
    if (target.getKey() != null) {
      builder.setKey(target.getKey());
    }

    return builder;
  }

  public static Protocol.CallEntryMessage.Builder invokeMessage(Target target, byte[] parameter) {
    return invokeMessage(target, Serde.RAW, parameter);
  }

  public static <T> Protocol.CallEntryMessage.Builder invokeMessage(
      Target target, Serde<T> reqSerde, T parameter) {
    return invokeMessage(target).setParameter(ByteString.copyFrom(reqSerde.serialize(parameter)));
  }

  public static <T, R> Protocol.CallEntryMessage invokeMessage(
      Target target, Serde<T> reqSerde, T parameter, Serde<R> resSerde, R result) {
    return invokeMessage(target, reqSerde, parameter)
        .setValue(ByteString.copyFrom(resSerde.serialize(result)))
        .build();
  }

  public static Protocol.CallEntryMessage.Builder invokeMessage(Target target, String parameter) {
    return invokeMessage(target, TestSerdes.STRING, parameter);
  }

  public static Protocol.CallEntryMessage invokeMessage(
      Target target, String parameter, String result) {
    return invokeMessage(target, TestSerdes.STRING, parameter, TestSerdes.STRING, result);
  }

  public static Protocol.AwakeableEntryMessage.Builder awakeable() {
    return Protocol.AwakeableEntryMessage.newBuilder();
  }

  public static Protocol.AwakeableEntryMessage awakeable(String value) {
    return awakeable().setValue(ByteString.copyFrom(TestSerdes.STRING.serialize(value))).build();
  }

  public static Protocol.GetPromiseEntryMessage.Builder getPromise(String key) {
    return Protocol.GetPromiseEntryMessage.newBuilder().setKey(key);
  }

  public static Protocol.PeekPromiseEntryMessage.Builder peekPromise(String key) {
    return Protocol.PeekPromiseEntryMessage.newBuilder().setKey(key);
  }

  public static Protocol.CompletePromiseEntryMessage.Builder completePromise(
      String key, String value) {
    return Protocol.CompletePromiseEntryMessage.newBuilder()
        .setKey(key)
        .setCompletionValue(ByteString.copyFrom(TestSerdes.STRING.serialize(value)));
  }

  public static Protocol.CompletePromiseEntryMessage.Builder completePromise(
      String key, Throwable e) {
    return Protocol.CompletePromiseEntryMessage.newBuilder()
        .setKey(key)
        .setCompletionFailure(Util.toProtocolFailure(e));
  }

  public static Java.CombinatorAwaitableEntryMessage combinatorsMessage(Integer... order) {
    return Java.CombinatorAwaitableEntryMessage.newBuilder()
        .addAllEntryIndex(Arrays.asList(order))
        .build();
  }

  public static final Protocol.EndMessage END_MESSAGE = Protocol.EndMessage.getDefaultInstance();

  public static final Target GREETER_SERVICE_TARGET = Target.service("Greeter", "greeter");
  public static Target GREETER_VIRTUAL_OBJECT_TARGET =
      Target.virtualObject("Greeter", "Francesco", "greeter");

  public static Protocol.GetStateKeysEntryMessage.StateKeys.Builder stateKeys(String... keys) {
    return Protocol.GetStateKeysEntryMessage.StateKeys.newBuilder()
        .addAllKeys(Arrays.stream(keys).map(ByteString::copyFromUtf8).collect(Collectors.toList()));
  }

  static MessageLite build(MessageLiteOrBuilder value) {
    if (value instanceof MessageLite) {
      return (MessageLite) value;
    } else {
      return ((MessageLite.Builder) value).build();
    }
  }
}
