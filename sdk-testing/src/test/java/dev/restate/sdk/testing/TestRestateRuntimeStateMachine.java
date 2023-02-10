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

class TestRestateRuntimeStateMachine<T>
    implements Flow.Processor<T, T>, Flow.Publisher<T>, Flow.Subscriber<T> {

  private static final Logger LOG = LogManager.getLogger(TestRestateRuntimeStateMachine.class);

  private HashMap<ByteString, ByteString> state = new HashMap<>();

  private final List<T> messages = new ArrayList<>();
  private final CompletableFuture<List<T>> future = new CompletableFuture<>();

  // Index tracking progress in the journal
  private int currentJournalIndex;

  // Flow subscriber
  // Subscription to get input from the services
  private Flow.Subscription inputSubscription;

  // Flow publisher
  // Elements to send to the service at startup
  private final Collection<T> elements;
  private final AtomicBoolean publisherSubscriptionCancelled;
  private Flow.Subscriber<? super T> publisher;

  public TestRestateRuntimeStateMachine(Collection<T> elements) {
    this.currentJournalIndex = 0;
    this.elements = elements;
    this.publisherSubscriptionCancelled = new AtomicBoolean(false);
  }

  // PUBLISHER LOGIC: to send messages to the service

  @Override
  public void subscribe(Flow.Subscriber<? super T> publisher) {
    this.publisher = publisher;
    publisher.onSubscribe(
        new MockPublisherSubscription<>(
            this.publisher, new ArrayDeque<>(elements), publisherSubscriptionCancelled));
  }

  public boolean getPublisherSubscriptionCancelled() {
    return publisherSubscriptionCancelled.get();
  }

  private static class MockPublisherSubscription<T> implements Flow.Subscription {

    private final Flow.Subscriber<? super T> publisher;
    private final Queue<T> queue;
    private final AtomicBoolean cancelled;

    private MockPublisherSubscription(
        Flow.Subscriber<? super T> publisher,
        Queue<T> queueToPublish,
        AtomicBoolean publisherSubscriptionCancelled) {
      this.publisher = publisher;
      this.queue = queueToPublish;
      this.cancelled = publisherSubscriptionCancelled;
    }

    @Override
    public void request(long l) {
      if (this.cancelled.get()) {
        return;
      }
      while (l != 0 && !this.queue.isEmpty()) {
        publisher.onNext(queue.remove());
      }
    }

    @Override
    public void cancel() {
      this.cancelled.set(true);
    }
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

    if (t instanceof Protocol.OutputStreamEntryMessage) {
      synchronized (this.messages) {
        this.messages.add(t);
      }
      onComplete();
    } else if (t instanceof Protocol.GetStateEntryMessage) {
      getAndSendState((Protocol.GetStateEntryMessage) t);
    } else if (t instanceof Protocol.SetStateEntryMessage) {
      setState((Protocol.SetStateEntryMessage) t);
    } else if (t instanceof Protocol.ClearStateEntryMessage) {
      clearState((Protocol.ClearStateEntryMessage) t);
    } else {
      System.out.println("Doesn't recognize this type: " + t.getClass().toGenericString());
    }
  }

  @Override
  public void onError(Throwable throwable) {
    this.future.completeExceptionally(throwable);
  }

  @Override
  public void onComplete() {
    List<T> l;
    synchronized (this.messages) {
      l = new ArrayList<>(this.messages);
    }
    if (inputSubscription != null) {
      this.inputSubscription.cancel();
    }
    if (this.publisher != null) {
      this.publisher.onComplete();
    }
    this.future.complete(l);
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
    LOG.debug("Received getStateEntryMessage: " + msg.toString());
    ByteString key = msg.getKey();
    ByteString value = state.get(key);
    if (value != null) {
      publisher.onNext((T) completionMessage(currentJournalIndex, value));
    } else {
      publisher.onNext((T) completionMessage(currentJournalIndex, Empty.getDefaultInstance()));
    }
  }

  private void setState(Protocol.SetStateEntryMessage msg) {
    LOG.debug("Received setStateEntryMessage: " + msg.toString());
    state.put(msg.getKey(), msg.getValue());
  }

  // Clears state for a single key
  private void clearState(Protocol.ClearStateEntryMessage msg) {
    LOG.debug("Received clearStateEntryMessage: " + msg.toString());
    state.remove(msg.getKey());
  }
}
