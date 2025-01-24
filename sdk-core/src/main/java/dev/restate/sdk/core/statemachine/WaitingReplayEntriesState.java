package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

final class WaitingReplayEntriesState implements State {

    private int receivedEntries = 0;
    private final Deque<MessageLite> commandsToProcess = new ArrayDeque<>();
    private final AsyncResultsState asyncResultsState = new AsyncResultsState();

    @Override
    public void onNewMessage(InvocationInput invocationInput, StateContext stateContext, CompletableFuture<Void> waitForReadyFuture) {
        if (invocationInput.header().getType().isNotification()) {
            if (!(invocationInput.message() instanceof Protocol.NotificationTemplate notificationTemplate)) {
                throw ProtocolException.unexpectedMessage(Protocol.NotificationTemplate.class, invocationInput.message());
            }

            this.asyncResultsState.enqueue(notificationTemplate);
        } else if (invocationInput.header().getType().isCommand()) {
            this.commandsToProcess.add(invocationInput.message());
        } else {
            throw ProtocolException.unexpectedMessage("command or notification", invocationInput.message());
        }

        this.receivedEntries++;

        if (stateContext.getStartInfo().entriesToReplay() == this.receivedEntries) {
            stateContext.getStateHolder().transition(
                    new ReplayingState(
                            commandsToProcess,
                            asyncResultsState
                    )
            );
            waitForReadyFuture.complete(null);
        }
    }

    @Override
    public void onInputClosed(StateContext stateContext, Flow.@Nullable Subscriber<? super MessageLite> outputSubscriber) {
        throw ProtocolException.inputClosedWhileWaitingEntries();
    }

    @Override
    public void end(StateContext stateContext, Flow.@Nullable Subscriber<? super MessageLite> outputSubscriber) {
        throw ProtocolException.closedWhileWaitingEntries();
    }

    @Override
    public InvocationState getInvocationState() {
        return InvocationState.WAITING_START;
    }
}
