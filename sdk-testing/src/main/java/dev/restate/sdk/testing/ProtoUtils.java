package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.Util;
import io.grpc.MethodDescriptor;
import java.util.Arrays;

public class ProtoUtils {

  public static Protocol.StartMessage.Builder startMessage(int entries) {
    return Protocol.StartMessage.newBuilder()
        .setInstanceKey(ByteString.copyFromUtf8("abc"))
        .setInvocationId(ByteString.copyFromUtf8("123"))
        .setKnownEntries(entries)
        .setKnownServiceVersion(1);
  }

  public static Protocol.StartMessage.Builder startMessage(String serviceName, String invocationId, int entries) {
    return Protocol.StartMessage.newBuilder()
            .setInstanceKey(ByteString.copyFromUtf8(serviceName))
            .setInvocationId(ByteString.copyFromUtf8(invocationId))
            .setKnownEntries(entries)
            .setKnownServiceVersion(1);
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

  static Protocol.OutputStreamEntryMessage outputMessage(Throwable e) {
    return Protocol.OutputStreamEntryMessage.newBuilder()
        .setFailure(Util.toProtocolFailure(e))
        .build();
  }

  // Messages Received from runtime state machine
  public static Protocol.CompletionMessage completionMessage(int index, String value) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setValue(ByteString.copyFromUtf8(value))
        .build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, ByteString value) {
    return Protocol.CompletionMessage.newBuilder().setEntryIndex(index).setValue(value).build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, Empty value) {
    return Protocol.CompletionMessage.newBuilder().setEntryIndex(index).setEmpty(value).build();
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
        .setFailure(Util.toProtocolFailure(e))
        .build();
  }

  // Message forwarded to runtime state machine
  static Protocol.GetStateEntryMessage.Builder getStateMessage(String key) {
    return Protocol.GetStateEntryMessage.newBuilder().setKey(ByteString.copyFromUtf8(key));
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
    return invokeMessage(methodDescriptor, parameter).setFailure(Util.toProtocolFailure(e)).build();
  }

  static Protocol.AwakeableEntryMessage.Builder awakeable() {
    return Protocol.AwakeableEntryMessage.newBuilder();
  }

  static Protocol.AwakeableEntryMessage awakeable(String value) {
    return awakeable().setValue(ByteString.copyFromUtf8(value)).build();
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
