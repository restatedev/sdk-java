package dev.restate.sdk.lambda;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.impl.InvocationFlow;
import dev.restate.sdk.core.impl.MessageHeader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class LambdaFlowAdapters {

  static class ResultSubscriber implements InvocationFlow.InvocationOutputSubscriber {

    private static final ByteBuffer LONG_CONVERSION_BUFFER = ByteBuffer.allocate(Long.BYTES);

    private final ByteArrayOutputStream outputStream;
    private boolean isCompleted;
    private Throwable completionError;

    ResultSubscriber() {
      this.outputStream = new ByteArrayOutputStream();
      this.isCompleted = false;
      this.completionError = null;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(MessageLite item) {
      LONG_CONVERSION_BUFFER.putLong(0, MessageHeader.fromMessage(item).encode());
      try {
        outputStream.write(LONG_CONVERSION_BUFFER.array());
        item.writeTo(outputStream);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      this.isCompleted = true;
      this.completionError = throwable;
    }

    @Override
    public void onComplete() {
      this.isCompleted = true;
    }

    public byte[] getResult() {
      if (isCompleted && completionError == null) {
        return outputStream.toByteArray();
      } else if (isCompleted) {
        throw (RuntimeException) completionError;
      } else {
        throw new IllegalStateException(
            "SDK Bug: Expected the computation to complete. Please contact the developers");
      }
    }
  }

  static class BufferedPublisher implements InvocationFlow.InvocationInputPublisher {

    private static final Logger LOG = LogManager.getLogger(BufferedPublisher.class);

    private final ByteBuffer buffer;

    BufferedPublisher(ByteBuffer buffer) {
      this.buffer = buffer.asReadOnlyBuffer();
    }

    private Flow.Subscriber<? super InvocationFlow.InvocationInput> inputMessagesSubscriber;
    private long subscriberRequest = 0;

    @Override
    public void subscribe(Flow.Subscriber<? super InvocationFlow.InvocationInput> subscriber) {
      if (this.inputMessagesSubscriber != null) {
        throw new IllegalStateException(
            "Cannot register more than one subscriber to this publisher");
      }
      // Make sure the new subscriber starts from beginning
      this.buffer.rewind();

      // Register the subscriber
      this.inputMessagesSubscriber = subscriber;
      this.inputMessagesSubscriber.onSubscribe(
          new Flow.Subscription() {
            @Override
            public void request(long l) {
              handleSubscriptionRequest(l);
            }

            @Override
            public void cancel() {
              cancelSubscription();
            }
          });
    }

    private void handleSubscriptionRequest(long l) {
      // Update the subscriber request
      if (l == Long.MAX_VALUE) {
        this.subscriberRequest = l;
      } else {
        this.subscriberRequest += l;
        // Overflow check
        if (this.subscriberRequest < 0) {
          this.subscriberRequest = Long.MAX_VALUE;
        }
      }

      // Now process the buffer
      while (this.subscriberRequest > 0 && this.inputMessagesSubscriber != null) {
        if (!buffer.hasRemaining()) {
          this.handleBufferEnd();
          return;
        }

        MessageHeader header;
        MessageLite entry;
        try {
          header = MessageHeader.parse(buffer.getLong());

          // Prepare the ByteBuffer and pass it to the Protobuf message parser
          ByteBuffer messageBuffer = buffer.slice();
          messageBuffer.limit(header.getLength());
          entry = header.getType().messageParser().parseFrom(messageBuffer);

          // Move the buffer after this message
          buffer.position(buffer.position() + header.getLength());
        } catch (InvalidProtocolBufferException | RuntimeException e) {
          handleDecodingError(e);
          return;
        }

        LOG.trace("Received entry " + entry);
        this.subscriberRequest--;
        inputMessagesSubscriber.onNext(InvocationFlow.InvocationInput.of(header, entry));
      }
    }

    private void handleDecodingError(Throwable e) {
      this.inputMessagesSubscriber.onError(e);
      this.cancelSubscription();
    }

    private void handleBufferEnd() {
      LOG.trace("Request end");
      this.inputMessagesSubscriber.onComplete();
      this.cancelSubscription();
    }

    private void cancelSubscription() {
      this.inputMessagesSubscriber = null;
    }
  }
}
