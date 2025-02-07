// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.UnsafeByteOperations;
import dev.restate.common.Slice;
import dev.restate.common.Target;
import dev.restate.sdk.core.ExceptionUtils;
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.core.generated.discovery.Discovery;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.generated.protocol.Protocol.StartMessage.StateEntry;
import dev.restate.serde.Serde;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProtoUtils {

  public static String serviceProtocolContentTypeHeader() {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        ServiceProtocol.MIN_SERVICE_PROTOCOL_VERSION);
  }

  public static String serviceProtocolContentTypeHeader(boolean enableContextPreview) {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        ServiceProtocol.MAX_SERVICE_PROTOCOL_VERSION);
  }

  public static String serviceProtocolDiscoveryContentTypeHeader() {
    return DiscoveryProtocol.serviceProtocolVersionToHeaderValue(
        Discovery.ServiceDiscoveryProtocolVersion.V2);
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

  public static ByteBuffer encodeMessageToByteBuffer(MessageLiteOrBuilder msgOrBuilder) {
    var msg = build(msgOrBuilder);
    return invocationInputToByteString(InvocationInput.of(headerFromMessage(msg), msg));
  }

  public static Slice encodeMessageToSlice(MessageLiteOrBuilder msgOrBuilder) {
    return Slice.wrap(encodeMessageToByteBuffer(msgOrBuilder));
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

  public static Protocol.SuspensionMessage suspensionMessage(Integer... completionIds) {
    return Protocol.SuspensionMessage.newBuilder().addAllEntryIndexes(List.of(completionIds)).build();
  }

  public static Protocol.InputCommandMessage inputMessage() {
    return Protocol.InputCommandMessage.newBuilder().setValue(ByteString.EMPTY).build();
  }

  public static Protocol.InputCommandMessage inputMessage(byte[] value) {
    return Protocol.InputCommandMessage.newBuilder().setValue(ByteString.copyFrom(value)).build();
  }

  public static <T> Protocol.InputCommandMessage inputMessage(Serde<T> serde, T value) {
    return Protocol.InputCommandMessage.newBuilder()
        .setValue(ByteString.copyFrom(serde.serialize(value)))
        .build();
  }

  public static Protocol.InputCommandMessage inputMessage(String value) {
    return inputMessage(TestSerdes.STRING, value);
  }

  public static Protocol.InputCommandMessage inputMessage(int value) {
    return inputMessage(TestSerdes.INT, value);
  }

  public static <T> Protocol.OutputCommandMessage outputMessage(Serde<T> serde, T value) {
    return Protocol.OutputCommandMessage.newBuilder()
        .setValue(ByteString.copyFrom(serde.serialize(value)))
        .build();
  }

  public static Protocol.OutputCommandMessage outputMessage(String value) {
    return outputMessage(TestSerdes.STRING, value);
  }

  public static Protocol.OutputCommandMessage outputMessage(int value) {
    return outputMessage(TestSerdes.INT, value);
  }

  public static Protocol.OutputCommandMessage outputMessage(byte[] b) {
    return outputMessage(Serde.RAW, b);
  }

  public static Protocol.OutputCommandMessage outputMessage() {
    return Protocol.OutputCommandMessage.newBuilder().setValue(ByteString.EMPTY).build();
  }

  public static Protocol.OutputCommandMessage outputMessage(int code, String message) {
    return Protocol.OutputCommandMessage.newBuilder()
        .setFailure(ExceptionUtils.toProtocolFailure(code, message))
        .build();
  }

  public static Protocol.OutputCommandMessage outputMessage(Throwable e) {
    return Protocol.OutputCommandMessage.newBuilder()
        .setFailure(ExceptionUtils.toProtocolFailure(e))
        .build();
  }

  public static Protocol.GetStateEntryMessage.Builder getStateMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(key));
  }

  public static Protocol.GetStateEntryMessage.Builder getStateMessage(String key, Throwable error) {
    return getStateMessage(key).setFailure(ExceptionUtils.toProtocolFailure(error));
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

  public static <T> Protocol.SetStateCommandMessage setStateMessage(
      String key, Serde<T> serde, T value) {
    return Protocol.SetStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(Protocol.Value.newBuilder().setContent(ByteString.copyFrom(serde.serialize(value).toByteArray())))
        .build();
  }

  public static Protocol.SetStateCommandMessage setStateMessage(String key, String value) {
    return setStateMessage(key, TestSerdes.STRING, value);
  }

  public static Protocol.ClearStateCommandMessage clearStateMessage(String key) {
    return Protocol.ClearStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .build();
  }

  public static Protocol.CallCommandMessage.Builder callCmd(int invocationIdCompletionId, int resultCompletionId, Target target) {
    Protocol.CallCommandMessage.Builder builder =
        Protocol.CallCommandMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler());
    if (target.getKey() != null) {
      builder.setKey(target.getKey());
    }
    builder.setInvocationIdNotificationIdx(invocationIdCompletionId)
            .setResultCompletionId(resultCompletionId);

    return builder;
  }

  public static Protocol.CallCommandMessage.Builder callCmd(int invocationIdCompletionId, int resultCompletionId, Target target, byte[] parameter) {
    return callCmd(invocationIdCompletionId, resultCompletionId, target, Serde.RAW, parameter);
  }

  public static <T> Protocol.CallCommandMessage.Builder callCmd(int invocationIdCompletionId,
                                                                int resultCompletionId, Target target, Serde<T> reqSerde, T parameter) {
    return callCmd(invocationIdCompletionId, resultCompletionId, target).setParameter(ByteString.copyFrom(reqSerde.serialize(parameter).toByteArray()));
  }

  public static Protocol.CallCommandMessage.Builder callCmd(int invocationIdCompletionId, int resultCompletionId, Target target, String parameter) {
    return callCmd(invocationIdCompletionId, resultCompletionId, target, TestSerdes.STRING, parameter);
  }

  public static <T> Protocol.CallCompletionNotificationMessage.Builder callCompletion(
          int completionId, Serde<T> reqSerde, T parameter) {
    return Protocol.CallCompletionNotificationMessage.newBuilder()
            .setCompletionId(completionId).setValue(Protocol.Value.newBuilder()
                    .setContent(
                            UnsafeByteOperations.unsafeWrap(
                            reqSerde.serialize(parameter).asReadOnlyByteBuffer()))
                    .build());
  }

  public static <T> Protocol.CallCompletionNotificationMessage.Builder callCompletion(
          int completionId, String result) {
    return callCompletion(completionId, TestSerdes.STRING, result);
  }

  public static <T> Protocol.CallInvocationIdCompletionNotificationMessage.Builder callInvocationIdCompletion(
          int completionId, String invocationId) {
    return Protocol.CallInvocationIdCompletionNotificationMessage.newBuilder()
            .setCompletionId(completionId).setInvocationId(invocationId);
  }

  public static Protocol.GetPromiseCommandMessage.Builder getPromise(String key) {
    return Protocol.GetPromiseCommandMessage.newBuilder().setKey(key);
  }

  public static Protocol.PeekPromiseCommandMessage.Builder peekPromise(String key) {
    return Protocol.PeekPromiseCommandMessage.newBuilder().setKey(key);
  }

  public static Protocol.CompletePromiseCommandMessage.Builder completePromise(
      String key, String value) {
    return Protocol.CompletePromiseCommandMessage.newBuilder()
        .setKey(key)
        .setCompletionValue(ByteString.copyFrom(TestSerdes.STRING.serialize(value)));
  }

  public static Protocol.CompletePromiseCommandMessage.Builder completePromise(
      String key, Throwable e) {
    return Protocol.CompletePromiseCommandMessage.newBuilder()
        .setKey(key)
        .setCompletionFailure(ExceptionUtils.toProtocolFailure(e));
  }

  public static final Protocol.EndMessage END_MESSAGE = Protocol.EndMessage.getDefaultInstance();

  public static final Target GREETER_SERVICE_TARGET = Target.service("Greeter", "greeter");
  public static Target GREETER_VIRTUAL_OBJECT_TARGET =
      Target.virtualObject("Greeter", "Francesco", "greeter");

  public static Protocol.GetStateKeysEntryMessage.StateKeys.Builder stateKeys(String... keys) {
    return Protocol.GetStateKeysEntryMessage.StateKeys.newBuilder()
        .addAllKeys(Arrays.stream(keys).map(ByteString::copyFromUtf8).collect(Collectors.toList()));
  }

  public static MessageLite build(MessageLiteOrBuilder value) {
    if (value instanceof MessageLite) {
      return (MessageLite) value;
    } else {
      return ((MessageLite.Builder) value).build();
    }
  }
}
