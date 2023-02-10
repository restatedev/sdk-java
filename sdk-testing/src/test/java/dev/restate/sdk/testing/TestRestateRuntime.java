package dev.restate.sdk.testing;

import static dev.restate.sdk.testing.ProtoUtils.completionMessage;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class TestRestateRuntime<T>
    implements Flow.Processor<T, T>, Flow.Publisher<T>, Flow.Subscriber<T> {

  private static final Logger LOG = LogManager.getLogger(TestRestateRuntime.class);

  // ID of the ongoing invocation (TEMPORARY: this doesn't allow for multiple services, multiple
  // calls, and inter-service calls)
  private String currentInvocationId = null;

  // Output of test
  private final List<T> messages = new ArrayList<>();
  private final CompletableFuture<List<T>> future = new CompletableFuture<>();

  // Index tracking progress in the journal
  private int currentJournalIndex;

  // Flow subscriber
  // Subscription to get input from the services
  private Flow.Subscription inputSubscription; // = subscription on InvocationStateMachine
  private Flow.Subscription outputSubscription; // = delivery path to publish to
  // ExceptionCatchingInvocationInputSubscriber

  // Flow publisher
  // Elements to send to the service at startup
  private final Collection<T> elements;
  private final AtomicBoolean publisherSubscriptionCancelled;
  private Flow.Subscriber<? super T>
      publisher; // publisher = ExceptionCatchingInvocationInputSubscriber

  public TestRestateRuntime(Collection<T> elements) {
    this.currentJournalIndex = 0;
    this.elements = elements;
    this.publisherSubscriptionCancelled = new AtomicBoolean(false);

    
  }

  // PUBLISHER LOGIC: to send messages to the service

  @Override
  public void subscribe(Flow.Subscriber<? super T> publisher) {
    this.publisher = publisher;
    this.outputSubscription =
        new MockPublishSubscription<T>(
            publisher, new ArrayDeque<>(elements), publisherSubscriptionCancelled, this);

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
  public void onNext(T t) {
    // increase the journal index because we received a new message
    currentJournalIndex++;

    routeMessage(t);
  }

  @Override
  public void onError(Throwable throwable) {
    this.future.completeExceptionally(throwable);
  }

  @Override
  public void onComplete() {
    LOG.trace("End of test: Gathering output messages");
    List<T> results = getMessages();
    if (inputSubscription != null) {
      LOG.trace("End of test: Canceling input subscription");
      this.inputSubscription.cancel();
    }
    if (this.publisher != null) {
      LOG.trace("End of test: Closing publisher");
      this.publisher.onComplete();
    }
    LOG.trace("End of test: Closing the runtime state machine");
    this.future.complete(results);
  }

  public CompletableFuture<List<T>> getFuture() {
    return future;
  }

  public List<T> getMessages() {
    List<T> l;
    synchronized (this.messages) {
      l = new ArrayList<>(this.messages);
    }
    return l;
  }

  private void getAndSendState(Protocol.GetStateEntryMessage msg) {
    LOG.trace("Received getStateEntryMessage: " + msg.toString());
    ByteString key = msg.getKey();
    ByteString value = StateStore.getInstance().get(key);
    if (value != null) {
      routeMessage((T) completionMessage(currentJournalIndex, value));
    } else {
      routeMessage((T) completionMessage(currentJournalIndex, Empty.getDefaultInstance()));
    }
  }

  private void setState(Protocol.SetStateEntryMessage msg) {
    LOG.trace("Received setStateEntryMessage: " + msg.toString());
    StateStore.getInstance().set(msg.getKey(), msg.getValue());
  }

  // Clears state for a single key
  private void clearState(Protocol.ClearStateEntryMessage msg) {
    LOG.trace("Received clearStateEntryMessage: " + msg.toString());
    StateStore.getInstance().clear(msg.getKey());
  }

  // All messages that go through the runtime go through this handler.
  protected void routeMessage(T t) {
    // we assume unary for now; when we receive an OutputStreamEntryMessage we assume the test is
    // done.
    if (t instanceof Protocol.StartMessage) {
      Protocol.StartMessage startMessage = (Protocol.StartMessage) t;
      this.currentInvocationId = startMessage.getInvocationId().toStringUtf8();

      LOG.trace("Sending start message for current Invocation ID {}", currentInvocationId);
      publisher.onNext(t);
    } else if (t instanceof Protocol.CompletionMessage) {
      LOG.trace("Sending completion message");
      publisher.onNext(t);
    } else if (t instanceof Protocol.PollInputStreamEntryMessage) {
      LOG.trace("Sending poll input stream message");
      publisher.onNext(t);
    } else if (t instanceof Protocol.OutputStreamEntryMessage) {
      synchronized (this.messages) {
        this.messages.add(t);
      }
      this.currentInvocationId = null;
      onComplete();
    } else if (t instanceof Protocol.GetStateEntryMessage) {
      getAndSendState((Protocol.GetStateEntryMessage) t);
    } else if (t instanceof Protocol.SetStateEntryMessage) {
      setState((Protocol.SetStateEntryMessage) t);
    } else if (t instanceof Protocol.ClearStateEntryMessage) {
      clearState((Protocol.ClearStateEntryMessage) t);
    } else if (t instanceof Protocol.SleepEntryMessage) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else if (t instanceof Protocol.InvokeEntryMessage) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else if (t instanceof Protocol.BackgroundInvokeEntryMessage) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else if (t instanceof Protocol.AwakeableEntryMessage) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else if (t instanceof Protocol.CompleteAwakeableEntryMessage) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else if (t instanceof Protocol.Failure) {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    } else {
      LOG.error(
          "This type is not yet implemented in the test runtime: "
              + t.getClass().toGenericString());
    }
  }
}
