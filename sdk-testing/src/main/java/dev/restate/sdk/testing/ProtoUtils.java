package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.Util;

public class ProtoUtils {

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

  public static Protocol.CompletionMessage completionMessage(int index, ByteString value) {
    return Protocol.CompletionMessage.newBuilder().setEntryIndex(index).setValue(value).build();
  }

  public static Protocol.CompletionMessage completionMessage(int index, Empty value) {
    return Protocol.CompletionMessage.newBuilder().setEntryIndex(index).setEmpty(value).build();
  }

  static MessageLite build(MessageLiteOrBuilder value) {
    if (value instanceof MessageLite) {
      return (MessageLite) value;
    } else {
      return ((MessageLite.Builder) value).build();
    }
  }
}
