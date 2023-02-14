package dev.restate.sdk.testing;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import dev.restate.generated.service.protocol.Protocol;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.restate.sdk.testing.ProtoUtils.completionMessage;

class InvocationProcessor<T> implements Flow.Processor<T, T>, Flow.Publisher<T>, Flow.Subscriber<T> {

    private static final Logger LOG = LogManager.getLogger(InvocationProcessor.class);

    private final String serviceName;
    private final String functionInvocationId;
    private final Collection<T> elements;

    private final StateStore stateStore;
    private final TestRestateRuntime testRestateRuntime;

    private final AtomicBoolean publisherSubscriptionCancelled;
    private Flow.Subscriber<? super T>
            publisher; // publisher = ExceptionCatchingInvocationInputSubscriber


    // Flow subscriber
    // Subscription to get input from the services
    private Flow.Subscription inputSubscription; // = subscription on InvocationStateMachine
    private Flow.Subscription outputSubscription; // = delivery path to publish to
    // ExceptionCatchingInvocationInputSubscriber

    // Index tracking progress in the journal
    private int currentJournalIndex;

    public InvocationProcessor(String serviceName,
                               String functionInvocationId,
                               Collection<T> elements,
                               TestRestateRuntime testRestateRuntime,
                               StateStore stateStore) {
        this.serviceName = serviceName;
        this.functionInvocationId = functionInvocationId;
        this.elements = elements;
        // TODO is there a cleaner way then to pass these along everywhere?
        this.testRestateRuntime = testRestateRuntime;
        this.stateStore = stateStore;
        this.publisherSubscriptionCancelled = new AtomicBoolean(false);
    }

    // PUBLISHER LOGIC: to send messages to the service

    @Override
    public void subscribe(Flow.Subscriber<? super T> publisher) {
        this.publisher = publisher;
        this.currentJournalIndex = 0;
        this.outputSubscription =
                // TODO elements
                new PublishSubscription<T>(
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
    public void onNext(T t) {
        // increase the journal index because we received a new message
        currentJournalIndex++;

        routeMessage(t);
    }

    @Override
    public void onError(Throwable throwable) {
        testRestateRuntime.onError(throwable);
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

        testRestateRuntime.onComplete();
    }

    private void getAndSendState(Protocol.GetStateEntryMessage msg) {
        LOG.trace("Received getStateEntryMessage: " + msg.toString());
        ByteString key = msg.getKey();
        ByteString value = stateStore.get(serviceName, key);
        if (value != null) {
            routeMessage((T) completionMessage(currentJournalIndex, value));
        } else {
            routeMessage((T) completionMessage(currentJournalIndex, Empty.getDefaultInstance()));
        }
    }

    public void handleInterServiceCallResult(Protocol.OutputStreamEntryMessage msg){
        Protocol.CompletionMessage completionMessage = Protocol.CompletionMessage.newBuilder()
                .setEntryIndex(currentJournalIndex)
                .setValue(msg.getValue())
                .build();
        routeMessage((T) completionMessage);
    }

    // All messages that go through the runtime go through this handler.
    protected void routeMessage(T t) {
        if (t instanceof Protocol.StartMessage) {
            throw new IllegalStateException("Start message should not end up in router.");
        } else if (t instanceof Protocol.CompletionMessage) {
            LOG.trace("Sending completion message");
            publisher.onNext(t);
        } else if (t instanceof Protocol.PollInputStreamEntryMessage) {
            LOG.trace("Sending poll input stream message");
            publisher.onNext(t);
        } else if (t instanceof Protocol.OutputStreamEntryMessage) {
            LOG.trace("Handling call result");
            testRestateRuntime.handleCallResult(functionInvocationId, (Protocol.OutputStreamEntryMessage) t);
            onComplete();
        } else if (t instanceof Protocol.GetStateEntryMessage) {
            getAndSendState((Protocol.GetStateEntryMessage) t);
        } else if (t instanceof Protocol.SetStateEntryMessage) {
            stateStore.set(serviceName, (Protocol.SetStateEntryMessage) t);
        } else if (t instanceof Protocol.ClearStateEntryMessage) {
            stateStore.clear(serviceName, (Protocol.ClearStateEntryMessage) t);
        } else if (t instanceof Protocol.SleepEntryMessage) {
            LOG.error(
                    "This type is not yet implemented in the test runtime: "
                            + t.getClass().toGenericString());
        } else if (t instanceof Protocol.InvokeEntryMessage) {
            LOG.trace("Handling invoke entry message");
            Protocol.InvokeEntryMessage invokeMsg = (Protocol.InvokeEntryMessage) t;
            // Let the runtime create an invocation processor to handle the call
            testRestateRuntime.handle(invokeMsg.getServiceName(),
                    invokeMsg.getMethodName(),
                    Protocol.PollInputStreamEntryMessage.newBuilder().setValue(invokeMsg.getParameter()).build(),
                    functionInvocationId);
        } else if (t instanceof Protocol.BackgroundInvokeEntryMessage) {
            LOG.trace("Handling background invoke entry message");
            Protocol.BackgroundInvokeEntryMessage invokeMsg = (Protocol.BackgroundInvokeEntryMessage) t;
            // Let the runtime create an invocation processor to handle the call
            // We set the caller id to "ignore" because we do not want a response.
            // The response will then be ignored out by runtime.
            testRestateRuntime.handle(invokeMsg.getServiceName(),
                    invokeMsg.getMethodName(),
                    Protocol.PollInputStreamEntryMessage.newBuilder().setValue(invokeMsg.getParameter()).build(),
                    "ignore");
            // Immediately send back acknowledgment of background call
            Protocol.CompletionMessage completionMessage = Protocol.CompletionMessage.newBuilder()
                    .setEntryIndex(currentJournalIndex)
                    .build();
            publisher.onNext((T) completionMessage);
        } else if (t instanceof Protocol.AwakeableEntryMessage) {
            LOG.error(
                    "This type is not yet implemented in the test runtime: "
                            + t.getClass().toGenericString());
        } else if (t instanceof Protocol.CompleteAwakeableEntryMessage) {
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