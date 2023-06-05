package dev.restate.sdk.testing;

import com.google.protobuf.MessageLite;
import dev.restate.generated.service.protocol.Protocol;
import dev.restate.sdk.core.impl.InvocationFlow;
import dev.restate.sdk.core.impl.MessageHeader;
import dev.restate.sdk.core.impl.MessageType;
import java.util.Queue;
import java.util.concurrent.Flow;

class PublishSubscription implements Flow.Subscription {
  private final Flow.Subscriber<? super InvocationFlow.InvocationInput> subscriber;
  private final Queue<MessageLite> queue;

  PublishSubscription(
      Flow.Subscriber<? super InvocationFlow.InvocationInput> subscriber,
      Queue<MessageLite> queue) {
    this.subscriber = subscriber;
    this.queue = queue;
  }

  @Override
  public void request(long l) {
    while (l != 0 && !this.queue.isEmpty()) {
      MessageLite msg = queue.remove();
      MessageHeader header = headerFromMessage(msg);
      subscriber.onNext(InvocationFlow.InvocationInput.of(header, msg));
    }
  }

  @Override
  public void cancel() {}

  static MessageHeader headerFromMessage(MessageLite msg) {
    if (msg instanceof Protocol.StartMessage) {
      return new MessageHeader(
          MessageType.StartMessage, MessageHeader.PARTIAL_STATE_FLAG, msg.getSerializedSize());
    } else if (msg instanceof Protocol.CompletionMessage) {
      return new MessageHeader(MessageType.CompletionMessage, (short) 0, msg.getSerializedSize());
    }
    return MessageHeader.fromMessage(msg);
  }
}
