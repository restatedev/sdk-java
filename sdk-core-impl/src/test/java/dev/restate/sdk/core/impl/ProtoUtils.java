package dev.restate.sdk.core.impl;

import static dev.restate.sdk.core.impl.Util.toProtocolFailure;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.generated.service.protocol.Protocol.StartMessage.StateEntry;
import dev.restate.sdk.core.impl.testservices.GreetingRequest;
import dev.restate.sdk.core.impl.testservices.GreetingResponse;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
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
      return new MessageHeader(
          MessageType.StartMessage, MessageHeader.PARTIAL_STATE_FLAG, msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, (short) 0, msg.getSerializedSize());
    }
    return MessageHeader.fromMessage(msg);
  }

  public static Protocol.StartMessage.Builder startMessage(int entries) {
    return Protocol.StartMessage.newBuilder()
        .setInstanceKey(ByteString.copyFromUtf8("abc"))
        .setInvocationId(ByteString.copyFromUtf8("123"))
        .setKnownEntries(entries);
  }

  @SafeVarargs
  public static Protocol.StartMessage.Builder startMessage(
      int entries, Map.Entry<String, String>... stateEntries) {
    return Protocol.StartMessage.newBuilder()
        .setInstanceKey(ByteString.copyFromUtf8("abc"))
        .setInvocationId(ByteString.copyFromUtf8("123"))
        .setKnownEntries(entries)
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

  static Protocol.CompletionMessage completionMessage(int index, Throwable e) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setFailure(toProtocolFailure(Status.INTERNAL.withDescription(e.getMessage())))
        .build();
  }

  static Protocol.SuspensionMessage suspensionMessage(Integer... indexes) {
    return Protocol.SuspensionMessage.newBuilder().addAllEntryIndexes(List.of(indexes)).build();
  }

  public static Protocol.PollInputStreamEntryMessage inputMessage(MessageLiteOrBuilder value) {
    return Protocol.PollInputStreamEntryMessage.newBuilder()
        .setValue(build(value).toByteString())
        .build();
  }

  static Protocol.OutputStreamEntryMessage outputMessage(MessageLiteOrBuilder value) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setValue(build(value).toByteString())
        .build();
  }

  static Protocol.OutputStreamEntryMessage outputMessage(Status s) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(s.asRuntimeException()))
        .build();
  }

  static Protocol.OutputStreamEntryMessage outputMessage(Throwable e) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setFailure(toProtocolFailure(Status.INTERNAL.withDescription(e.getMessage())))
        .build();
  }

  static Protocol.GetStateEntryMessage.Builder getStateMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(key));
  }

  static Protocol.GetStateEntryMessage getStateEmptyMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setEmpty(Empty.getDefaultInstance())
        .build();
  }

  static Protocol.GetStateEntryMessage getStateMessage(String key, String value) {
    return getStateMessage(key).setValue(ByteString.copyFromUtf8(value)).build();
  }

  static Protocol.SetStateEntryMessage setStateMessage(String key, String value) {
    return Protocol.SetStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .setValue(ByteString.copyFromUtf8(value))
        .build();
  }

  static Protocol.ClearStateEntryMessage clearStateMessage(String key) {
    return Protocol.ClearStateEntryMessage.newBuilder()
        .setKey(ByteString.copyFromUtf8(key))
        .build();
  }

  static <T extends MessageLite, R extends MessageLite>
      Protocol.InvokeEntryMessage.Builder invokeMessage(
          MethodDescriptor<T, R> methodDescriptor, T parameter) {
    return Protocol.InvokeEntryMessage.newBuilder()
        .setServiceName(methodDescriptor.getServiceName())
        .setMethodName(methodDescriptor.getBareMethodName())
        .setParameter(parameter.toByteString());
  }

  static <T extends MessageLite, R extends MessageLite> Protocol.InvokeEntryMessage invokeMessage(
      MethodDescriptor<T, R> methodDescriptor, T parameter, R result) {
    return invokeMessage(methodDescriptor, parameter).setValue(result.toByteString()).build();
  }

  static <T extends MessageLite, R extends MessageLite> Protocol.InvokeEntryMessage invokeMessage(
      MethodDescriptor<T, R> methodDescriptor, T parameter, Throwable e) {
    return invokeMessage(methodDescriptor, parameter)
        .setFailure(toProtocolFailure(Status.INTERNAL.withDescription(e.getMessage())))
        .build();
  }

  static Protocol.AwakeableEntryMessage.Builder awakeable() {
    return Protocol.AwakeableEntryMessage.newBuilder();
  }

  static Protocol.AwakeableEntryMessage awakeable(String value) {
    return awakeable().setValue(ByteString.copyFromUtf8(value)).build();
  }

  static GreetingRequest greetingRequest(String name) {
    return GreetingRequest.newBuilder().setName(name).build();
  }

  static GreetingResponse greetingResponse(String message) {
    return GreetingResponse.newBuilder().setMessage(message).build();
  }

  static Java.CombinatorAwaitableEntryMessage combinatorsMessage(Integer... order) {
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
