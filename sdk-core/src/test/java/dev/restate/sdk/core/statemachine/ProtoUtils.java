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
import dev.restate.sdk.core.TestSerdes;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.generated.protocol.Protocol.StartMessage.StateEntry;
import dev.restate.serde.Serde;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class ProtoUtils {

  public static long invocationIdToRandomSeed(String invocationId) {
    return new InvocationIdImpl(invocationId).toRandomSeed();
  }

  public static String serviceProtocolContentTypeHeader() {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        ServiceProtocol.MIN_SERVICE_PROTOCOL_VERSION);
  }

  public static String serviceProtocolContentTypeHeader(boolean enableContextPreview) {
    return ServiceProtocol.serviceProtocolVersionToHeaderValue(
        ServiceProtocol.MAX_SERVICE_PROTOCOL_VERSION);
  }

  public static String serviceProtocolDiscoveryContentTypeHeader() {
    return "application/vnd.restate.endpointmanifest.v2+json";
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
    return invocationInputToByteString(InvocationInput.of(MessageHeader.fromMessage(msg), msg));
  }

  public static Slice encodeMessageToSlice(MessageLiteOrBuilder msgOrBuilder) {
    return Slice.wrap(encodeMessageToByteBuffer(msgOrBuilder));
  }

  public static List<MessageLite> bufferToMessages(List<ByteBuffer> byteBuffers) {
    var messageDecoder = new MessageDecoder();
    byteBuffers.stream().map(Slice::wrap).forEach(messageDecoder::offer);

    var outputList = new ArrayList<InvocationInput>();
    while (messageDecoder.isNextAvailable()) {
      outputList.add(messageDecoder.next());
    }
    return outputList.stream().map(InvocationInput::message).collect(Collectors.toList());
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
                                ByteString.copyFrom(
                                    TestSerdes.STRING.serialize(e.getValue()).toByteArray()))
                            .build())
                .collect(Collectors.toList()));
  }

  public static Protocol.SuspensionMessage suspensionMessage(Integer... completionIds) {
    return Protocol.SuspensionMessage.newBuilder()
        .addAllWaitingCompletions(List.of(completionIds))
        .addWaitingSignals(1)
        .build();
  }

  public static Protocol.InputCommandMessage inputCmd() {
    return Protocol.InputCommandMessage.newBuilder()
        .setValue(Protocol.Value.newBuilder().setContent(ByteString.EMPTY))
        .build();
  }

  public static Protocol.InputCommandMessage inputCmd(byte[] value) {
    return Protocol.InputCommandMessage.newBuilder()
        .setValue(Protocol.Value.newBuilder().setContent(ByteString.copyFrom(value)))
        .build();
  }

  public static <T> Protocol.InputCommandMessage inputCmd(Serde<T> serde, T value) {
    return Protocol.InputCommandMessage.newBuilder().setValue(value(serde, value)).build();
  }

  public static Protocol.InputCommandMessage inputCmd(String value) {
    return inputCmd(TestSerdes.STRING, value);
  }

  public static Protocol.InputCommandMessage inputCmd(int value) {
    return inputCmd(TestSerdes.INT, value);
  }

  public static <T> Protocol.OutputCommandMessage outputCmd(Serde<T> serde, T value) {
    return Protocol.OutputCommandMessage.newBuilder().setValue(value(serde, value)).build();
  }

  public static Protocol.OutputCommandMessage outputCmd(String value) {
    return outputCmd(TestSerdes.STRING, value);
  }

  public static Protocol.OutputCommandMessage outputCmd(int value) {
    return outputCmd(TestSerdes.INT, value);
  }

  public static Protocol.OutputCommandMessage outputCmd(byte[] b) {
    return outputCmd(Serde.RAW, b);
  }

  public static Protocol.OutputCommandMessage outputCmd() {
    return Protocol.OutputCommandMessage.newBuilder()
        .setValue(Protocol.Value.newBuilder().setContent(ByteString.empty()).build())
        .build();
  }

  public static Protocol.OutputCommandMessage outputCmd(int code, String message) {
    return Protocol.OutputCommandMessage.newBuilder().setFailure(failure(code, message)).build();
  }

  public static Protocol.OutputCommandMessage outputCmd(Throwable e) {
    return Protocol.OutputCommandMessage.newBuilder().setFailure(failure(e)).build();
  }

  public static Protocol.GetLazyStateCommandMessage.Builder getLazyStateCmd(
      int completionId, String key) {
    return Protocol.GetLazyStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setResultCompletionId(completionId);
  }

  public static Protocol.GetEagerStateCommandMessage getEagerStateEmptyCmd(String key) {
    return Protocol.GetEagerStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setVoid(Protocol.Void.getDefaultInstance())
        .build();
  }

  public static <T> Protocol.GetEagerStateCommandMessage getEagerStateCmd(
      String key, Serde<T> serde, T value) {
    return Protocol.GetEagerStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(value(serde, value))
        .build();
  }

  public static Protocol.GetEagerStateCommandMessage getEagerStateCmd(String key, String value) {
    return getEagerStateCmd(key, TestSerdes.STRING, value);
  }

  public static <T> Protocol.GetLazyStateCompletionNotificationMessage getLazyStateCompletion(
      int completionId, Serde<T> serde, T value) {
    return Protocol.GetLazyStateCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setValue(
            Protocol.Value.newBuilder()
                .setContent(
                    UnsafeByteOperations.unsafeWrap(serde.serialize(value).asReadOnlyByteBuffer())))
        .build();
  }

  public static Protocol.GetLazyStateCompletionNotificationMessage getLazyStateCompletion(
      int completionId, String value) {
    return getLazyStateCompletion(completionId, TestSerdes.STRING, value);
  }

  public static Protocol.GetLazyStateCompletionNotificationMessage getLazyStateCompletionEmpty(
      int completionId) {
    return Protocol.GetLazyStateCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setVoid(Protocol.Void.getDefaultInstance())
        .build();
  }

  public static <T> Protocol.SetStateCommandMessage setStateCmd(
      String key, Serde<T> serde, T value) {
    return Protocol.SetStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(
            Protocol.Value.newBuilder()
                .setContent(ByteString.copyFrom(serde.serialize(value).toByteArray())))
        .build();
  }

  public static Protocol.SetStateCommandMessage setStateCmd(String key, String value) {
    return setStateCmd(key, TestSerdes.STRING, value);
  }

  public static Protocol.ClearStateCommandMessage clearStateCmd(String key) {
    return Protocol.ClearStateCommandMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .build();
  }

  public static Protocol.CallCommandMessage.Builder callCmd(
      int invocationIdCompletionId, int resultCompletionId, Target target) {
    Protocol.CallCommandMessage.Builder builder =
        Protocol.CallCommandMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler());
    if (target.getKey() != null) {
      builder.setKey(target.getKey());
    }
    builder
        .setInvocationIdNotificationIdx(invocationIdCompletionId)
        .setResultCompletionId(resultCompletionId);

    return builder;
  }

  public static Protocol.CallCommandMessage.Builder callCmd(
      int invocationIdCompletionId, int resultCompletionId, Target target, byte[] parameter) {
    return callCmd(invocationIdCompletionId, resultCompletionId, target, Serde.RAW, parameter);
  }

  public static <T> Protocol.CallCommandMessage.Builder callCmd(
      int invocationIdCompletionId,
      int resultCompletionId,
      Target target,
      Serde<T> reqSerde,
      T parameter) {
    return callCmd(invocationIdCompletionId, resultCompletionId, target)
        .setParameter(ByteString.copyFrom(reqSerde.serialize(parameter).toByteArray()));
  }

  public static Protocol.CallCommandMessage.Builder callCmd(
      int invocationIdCompletionId, int resultCompletionId, Target target, String parameter) {
    return callCmd(
        invocationIdCompletionId, resultCompletionId, target, TestSerdes.STRING, parameter);
  }

  public static Protocol.OneWayCallCommandMessage.Builder oneWayCallCmd(
      int invocationIdCompletionId,
      Target target,
      @Nullable String idempotencyKey,
      @Nullable Map<String, String> headers,
      Slice input) {
    Protocol.OneWayCallCommandMessage.Builder builder =
        Protocol.OneWayCallCommandMessage.newBuilder()
            .setServiceName(target.getService())
            .setHandlerName(target.getHandler());
    if (target.getKey() != null) {
      builder.setKey(target.getKey());
    }
    if (idempotencyKey != null) {
      builder.setIdempotencyKey(idempotencyKey);
    }
    if (headers != null) {
      builder.addAllHeaders(
          headers.entrySet().stream()
              .map(
                  e ->
                      Protocol.Header.newBuilder()
                          .setKey(e.getKey())
                          .setValue(e.getValue())
                          .build())
              .toList());
    }

    builder
        .setParameter(UnsafeByteOperations.unsafeWrap(input.asReadOnlyByteBuffer()))
        .setInvocationIdNotificationIdx(invocationIdCompletionId);

    return builder;
  }

  public static <T> Protocol.CallCompletionNotificationMessage.Builder callCompletion(
      int completionId, Serde<T> reqSerde, T parameter) {
    return Protocol.CallCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setValue(value(reqSerde, parameter));
  }

  public static Protocol.CallCompletionNotificationMessage.Builder callCompletion(
      int completionId, String result) {
    return callCompletion(completionId, TestSerdes.STRING, result);
  }

  public static Protocol.CallCompletionNotificationMessage.Builder callCompletion(
      int completionId, Throwable failure) {
    return Protocol.CallCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setFailure(failure(failure));
  }

  public static <T>
      Protocol.CallInvocationIdCompletionNotificationMessage.Builder callInvocationIdCompletion(
          int completionId, String invocationId) {
    return Protocol.CallInvocationIdCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setInvocationId(invocationId);
  }

  public static Protocol.GetPromiseCommandMessage.Builder getPromiseCmd(
      int completionId, String key) {
    return Protocol.GetPromiseCommandMessage.newBuilder()
        .setResultCompletionId(completionId)
        .setKey(key);
  }

  public static Protocol.PeekPromiseCommandMessage.Builder peekPromiseCmd(
      int completionId, String key) {
    return Protocol.PeekPromiseCommandMessage.newBuilder()
        .setResultCompletionId(completionId)
        .setKey(key);
  }

  public static Protocol.CompletePromiseCommandMessage.Builder completePromiseCmd(
      int completionId, String key, String value) {
    return Protocol.CompletePromiseCommandMessage.newBuilder()
        .setKey(key)
        .setResultCompletionId(completionId)
        .setCompletionValue(value(value));
  }

  public static Protocol.CompletePromiseCommandMessage.Builder completePromiseCmd(
      int completionId, String key, Throwable e) {
    return Protocol.CompletePromiseCommandMessage.newBuilder()
        .setKey(key)
        .setResultCompletionId(completionId)
        .setCompletionFailure(failure(e));
  }

  public static <T> Protocol.SignalNotificationMessage signalNotification(
      int signalId, Serde<T> serde, T value) {
    return Protocol.SignalNotificationMessage.newBuilder()
        .setIdx(signalId)
        .setValue(
            Protocol.Value.newBuilder()
                .setContent(
                    UnsafeByteOperations.unsafeWrap(serde.serialize(value).asReadOnlyByteBuffer())))
        .build();
  }

  public static Protocol.SignalNotificationMessage signalNotification(int signalId, String value) {
    return signalNotification(signalId, TestSerdes.STRING, value);
  }

  public static <T> Protocol.SignalNotificationMessage signalNotification(
      String signalName, Serde<T> serde, T value) {
    return Protocol.SignalNotificationMessage.newBuilder()
        .setName(signalName)
        .setValue(
            Protocol.Value.newBuilder()
                .setContent(
                    UnsafeByteOperations.unsafeWrap(serde.serialize(value).asReadOnlyByteBuffer())))
        .build();
  }

  public static Protocol.SignalNotificationMessage signalNotification(
      String signalName, String value) {
    return signalNotification(signalName, TestSerdes.STRING, value);
  }

  public static Protocol.RunCommandMessage runCmd(int completion) {
    return Protocol.RunCommandMessage.newBuilder().setResultCompletionId(completion).build();
  }

  public static Protocol.RunCommandMessage runCmd(int completion, String name) {
    return Protocol.RunCommandMessage.newBuilder()
        .setResultCompletionId(completion)
        .setName(name)
        .build();
  }

  public static <T> Protocol.RunCompletionNotificationMessage.Builder runCompletion(
      int completionId, Serde<T> reqSerde, T parameter) {
    return Protocol.RunCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setValue(value(reqSerde, parameter));
  }

  public static Protocol.RunCompletionNotificationMessage.Builder runCompletion(
      int completionId, String result) {
    return runCompletion(completionId, TestSerdes.STRING, result);
  }

  public static Protocol.RunCompletionNotificationMessage.Builder runCompletion(
      int completionId, int code, String message) {
    return Protocol.RunCompletionNotificationMessage.newBuilder()
        .setCompletionId(completionId)
        .setFailure(failure(code, message));
  }

  public static <T> Protocol.ProposeRunCompletionMessage.Builder proposeRunCompletion(
      int completionId, Serde<T> reqSerde, T parameter) {
    return Protocol.ProposeRunCompletionMessage.newBuilder()
        .setResultCompletionId(completionId)
        .setValue(value(reqSerde, parameter).getContent());
  }

  public static Protocol.ProposeRunCompletionMessage.Builder proposeRunCompletion(
      int completionId, String result) {
    return proposeRunCompletion(completionId, TestSerdes.STRING, result);
  }

  public static Protocol.ProposeRunCompletionMessage.Builder proposeRunCompletion(
      int completionId, int code, String message) {
    return Protocol.ProposeRunCompletionMessage.newBuilder()
        .setResultCompletionId(completionId)
        .setFailure(failure(code, message));
  }

  public static Protocol.SendSignalCommandMessage sendCancelSignal(String targetInvocationId) {
    return Protocol.SendSignalCommandMessage.newBuilder()
        .setTargetInvocationId(targetInvocationId)
        .setIdx(1)
        .setVoid(Protocol.Void.getDefaultInstance())
        .build();
  }

  public static Protocol.Failure failure(int code, String message) {
    return Util.toProtocolFailure(code, message);
  }

  public static Protocol.Failure failure(Throwable throwable) {
    return Util.toProtocolFailure(throwable);
  }

  public static Protocol.Value value(String jsonStringContent) {
    return value(TestSerdes.STRING, jsonStringContent);
  }

  public static <T> Protocol.Value value(Serde<T> serde, T value) {
    return Protocol.Value.newBuilder()
        .setContent(UnsafeByteOperations.unsafeWrap(serde.serialize(value).asReadOnlyByteBuffer()))
        .build();
  }

  public static final Protocol.EndMessage END_MESSAGE = Protocol.EndMessage.getDefaultInstance();
  public static final Protocol.SignalNotificationMessage CANCELLATION_SIGNAL =
      Protocol.SignalNotificationMessage.newBuilder()
          .setVoid(Protocol.Void.getDefaultInstance())
          .setIdx(1)
          .build();

  public static final Target GREETER_SERVICE_TARGET = Target.service("Greeter", "greeter");
  public static Target GREETER_VIRTUAL_OBJECT_TARGET =
      Target.virtualObject("Greeter", "Francesco", "greeter");

  public static Protocol.StateKeys.Builder stateKeys(String... keys) {
    return Protocol.StateKeys.newBuilder()
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
