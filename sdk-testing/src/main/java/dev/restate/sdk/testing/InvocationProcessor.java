package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.MessageLite;
import dev.restate.generated.sdk.java.Java;
import dev.restate.generated.service.protocol.Protocol;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InvocationProcessor
    implements Flow.Processor<MessageLite, MessageLite>,
        Flow.Publisher<MessageLite>,
        Flow.Subscriber<MessageLite> {

  private static final Logger LOG = LogManager.getLogger(InvocationProcessor.class);

  private final String serviceName;
  private final String instanceKey;
  private final String functionInvocationId;
  private final Collection<MessageLite> elements;

  private final AtomicBoolean publisherSubscriptionCancelled;
  private Flow.Subscriber<? super MessageLite>
      publisher; // publisher = ExceptionCatchingInvocationInputSubscriber

  // Flow subscriber
  // Subscription to get input from the services
  private Flow.Subscription inputSubscription; // = subscription on InvocationStateMachine
  private Flow.Subscription outputSubscription; // = delivery path to publish to
  // ExceptionCatchingInvocationInputSubscriber

  // Index tracking progress in the journal
  private int currentJournalIndex;

  public InvocationProcessor(
      String serviceName,
      String instanceKey,
      String functionInvocationId,
      Collection<MessageLite> elements) {
    this.serviceName = serviceName;
    this.instanceKey = instanceKey;
    this.functionInvocationId = functionInvocationId;
    this.elements = elements;
    this.publisherSubscriptionCancelled = new AtomicBoolean(false);
  }

  // PUBLISHER LOGIC: to send messages to the service

  @Override
  public void subscribe(Flow.Subscriber<? super MessageLite> publisher) {
    this.publisher = publisher;
    this.currentJournalIndex = 0;
    this.outputSubscription =
        new PublishSubscription<MessageLite>(
            publisher, new ArrayDeque<>(elements), publisherSubscriptionCancelled);

    publisher.onSubscribe(this.outputSubscription);
  }

  public boolean getPublisherSubscriptionCancelled() {
    return publisherSubscriptionCancelled.get();
  }

  // SUBSCRIBER LOGIC: to receive input from the service

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.inputSubscription = subscription;
    this.inputSubscription.request(Long.MAX_VALUE);
  }

  // Called for each message that comes in. Sent by the service to the runtime.
  @Override
  public void onNext(MessageLite msg) {
    // increase the journal index because we received a new message
    currentJournalIndex++;

    routeMessage(msg);
  }

  @Override
  public void onError(Throwable throwable) {
    TestRestateRuntime.get().onError(throwable);
  }

  @Override
  public void onComplete() {
    LOG.trace("End of test: Gathering output messages");
    if (inputSubscription != null) {
      LOG.trace("End of test: Canceling input subscription");
      this.inputSubscription.cancel();
    }
    if (this.publisher != null) {
      LOG.trace("End of test: Closing publisher");
      this.publisher.onComplete();
    }
    LOG.trace("End of test: Closing the runtime state machine");

    TestRestateRuntime.get().onComplete(functionInvocationId);
  }

  public void handleCompletionMessage(ByteString value) {
    routeMessage(
        Protocol.CompletionMessage.newBuilder()
            .setEntryIndex(currentJournalIndex)
            .setValue(value)
            .build());
  }

  // All messages that go through the runtime go through this handler.
  protected void routeMessage(MessageLite t) {
    if (t instanceof Protocol.CompletionMessage) {
      LOG.trace("Sending completion message");
      publisher.onNext(t);

    } else if (t instanceof Protocol.PollInputStreamEntryMessage) {
      LOG.trace("Sending poll input stream message");
      publisher.onNext(t);

    } else if (t instanceof Protocol.OutputStreamEntryMessage) {
      LOG.trace("Handling call result");
      TestRestateRuntime.get()
          .handleCallResult(functionInvocationId, (Protocol.OutputStreamEntryMessage) t);
      onComplete();

    } else if (t instanceof Protocol.GetStateEntryMessage) {
      Protocol.GetStateEntryMessage msg = (Protocol.GetStateEntryMessage) t;
      LOG.trace("Received GetStateEntryMessage: " + msg);
      ByteString value = StateStore.get().get(serviceName, instanceKey, msg.getKey());
      if (value != null) {
        routeMessage(
            Protocol.CompletionMessage.newBuilder()
                .setEntryIndex(currentJournalIndex)
                .setValue(value)
                .build());
      } else {
        routeMessage(
            Protocol.CompletionMessage.newBuilder()
                .setEntryIndex(currentJournalIndex)
                .setEmpty(Empty.getDefaultInstance())
                .build());
      }

    } else if (t instanceof Protocol.SetStateEntryMessage) {
      Protocol.SetStateEntryMessage msg = (Protocol.SetStateEntryMessage) t;
      LOG.trace("Received SetStateEntryMessage: " + msg);
      StateStore.get().set(serviceName, instanceKey, msg.getKey(), msg.getValue());

    } else if (t instanceof Protocol.ClearStateEntryMessage) {
      Protocol.ClearStateEntryMessage msg = (Protocol.ClearStateEntryMessage) t;
      LOG.trace("Received ClearStateEntryMessage: " + msg);
      StateStore.get().clear(serviceName, instanceKey, msg.getKey());

    } else if (t instanceof Protocol.InvokeEntryMessage) {
      Protocol.InvokeEntryMessage msg = (Protocol.InvokeEntryMessage) t;
      LOG.trace("Handling InvokeEntryMessage: " + msg);

      // Let the runtime create an invocation processor to handle the call
      TestRestateRuntime.get()
          .handle(
              msg.getServiceName(),
              msg.getMethodName(),
              Protocol.PollInputStreamEntryMessage.newBuilder()
                  .setValue(msg.getParameter())
                  .build(),
              functionInvocationId);

    } else if (t instanceof Protocol.BackgroundInvokeEntryMessage) {
      Protocol.BackgroundInvokeEntryMessage msg = (Protocol.BackgroundInvokeEntryMessage) t;
      LOG.trace("Handling BackgroundInvokeEntryMessage: " + msg);

      // Let the runtime create an invocation processor to handle the call
      // We set the caller id to "ignore" because we do not want a response.
      // The response will then be ignored by runtime.
      TestRestateRuntime.get()
          .handle(
              msg.getServiceName(),
              msg.getMethodName(),
              Protocol.PollInputStreamEntryMessage.newBuilder()
                  .setValue(msg.getParameter())
                  .build(),
              "ignore");

    } else if (t instanceof Java.SideEffectEntryMessage) {
      Java.SideEffectEntryMessage msg = (Java.SideEffectEntryMessage) t;
      LOG.trace("Received SideEffectEntryMessage: " + msg);
      // Immediately send back acknowledgment of side effect
      Protocol.CompletionMessage completionMessage =
          Protocol.CompletionMessage.newBuilder().setEntryIndex(currentJournalIndex).build();
      publisher.onNext(completionMessage);

    } else if (t instanceof Protocol.AwakeableEntryMessage) {
      Protocol.AwakeableEntryMessage msg = (Protocol.AwakeableEntryMessage) t;
      LOG.trace("Received AwakeableEntryMessage: " + msg);
      // The test runtime doesn't do anything with these messages.

    } else if (t instanceof Protocol.CompleteAwakeableEntryMessage) {
      Protocol.CompleteAwakeableEntryMessage msg = (Protocol.CompleteAwakeableEntryMessage) t;
      LOG.trace("Received CompleteAwakeableEntryMessage: " + msg);
      TestRestateRuntime.get().handleAwakeableCompletion(msg.getInvocationId().toStringUtf8(), msg);

    } else if (t instanceof Java.CombinatorAwaitableEntryMessage) {
      Java.CombinatorAwaitableEntryMessage msg = (Java.CombinatorAwaitableEntryMessage) t;
      LOG.trace("Received CombinatorAwaitableEntryMessage: " + msg);
      // The test runtime doesn't do anything with these messages.

    } else if (t instanceof Protocol.SleepEntryMessage) {
      throw new IllegalStateException(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());

    } else if (t instanceof Protocol.StartMessage) {
      throw new IllegalStateException("Start message should not end up in router.");
    } else {
      throw new IllegalStateException(
          "This type is not implemented in the test runtime: " + t.getClass().toGenericString());
    }
  }
}
