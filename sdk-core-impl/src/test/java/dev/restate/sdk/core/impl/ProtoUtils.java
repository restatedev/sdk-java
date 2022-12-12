package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;

public class ProtoUtils {

  static Protocol.StartMessage.Builder startMessage(int entries) {
    return Protocol.StartMessage.newBuilder()
        .setInstanceKey(ByteString.copyFromUtf8("abc"))
        .setInvocationId(ByteString.copyFromUtf8("123"))
        .setKnownEntries(entries)
        .setKnownServiceVersion(1);
  }

  static Protocol.CompletionMessage completionMessage(int index, String value) {
    return Protocol.CompletionMessage.newBuilder()
        .setEntryIndex(index)
        .setValue(ByteString.copyFromUtf8(value))
        .build();
  }

  static Protocol.PollInputStreamEntryMessage inputMessage(MessageLiteOrBuilder value) {
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

  static Java.CompletionOrderEntryMessage orderMessage(int index) {
    return Java.CompletionOrderEntryMessage.newBuilder().setEntryIndex(index).build();
  }

  static MessageLite build(MessageLiteOrBuilder value) {
    if (value instanceof MessageLite) {
      return (MessageLite) value;
    } else {
      return ((MessageLite.Builder) value).build();
    }
  }
}
