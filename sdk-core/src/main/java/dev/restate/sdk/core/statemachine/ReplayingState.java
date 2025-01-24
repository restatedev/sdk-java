package dev.restate.sdk.core.statemachine;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.sdk.core.ExceptionUtils;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.StateMachine.DoProgressResponse;
import dev.restate.sdk.types.AbortedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class ReplayingState implements State {

    private static final Logger LOG = LogManager.getLogger(ReplayingState.class);

    private final Deque<MessageLite> commandsToProcess ;
    private final AsyncResultsState asyncResultsState;
    private final RunState runState;

    ReplayingState(Deque<MessageLite> commandsToProcess, AsyncResultsState asyncResultsState) {
        this.commandsToProcess = commandsToProcess;
        this.asyncResultsState = asyncResultsState;
        this.runState = new RunState();
    }

    @Override
    public void onNewMessage(InvocationInput invocationInput, StateContext stateContext, CompletableFuture<Void> waitForReadyFuture) {
        if (invocationInput.header().getType().isNotification()) {
            if (!(invocationInput.message() instanceof Protocol.NotificationTemplate notificationTemplate)) {
                throw ProtocolException.unexpectedMessage(Protocol.NotificationTemplate.class, invocationInput.message());
            }
            this.asyncResultsState.enqueue(notificationTemplate);
        } else {
            throw ProtocolException.unexpectedMessage("notification", invocationInput.message());
        }
    }

    @Override
    public DoProgressResponse doProgress(List<Integer> awaitingOn, StateContext stateContext) {
        if (awaitingOn.stream().anyMatch(this.asyncResultsState::isHandleCompleted)) {
            return DoProgressResponse.AnyCompleted.INSTANCE;
        }

        var notificationIds = asyncResultsState.resolveNotificationHandles(awaitingOn);
        if (notificationIds.isEmpty()) {
            return DoProgressResponse.AnyCompleted.INSTANCE;
        }

        if (asyncResultsState.processNextUntilAnyFound(notificationIds)) {
            return DoProgressResponse.AnyCompleted.INSTANCE;
        }

        if (stateContext.isInputClosed()) {
            this.hitSuspended(notificationIds, stateContext);
            ExceptionUtils.sneakyThrow(AbortedExecutionException.INSTANCE);
        }

        return DoProgressResponse.ReadFromInput.INSTANCE;
    }

    @Override
    public Optional<NotificationValue> takeNotification(int handle, StateContext stateContext) {
        return this.asyncResultsState.takeHandle(handle);
    }

    @Override
    public int processRunCommand(String name, StateContext stateContext) {
        var completionId = stateContext.getJournal().nextCompletionNotificationId();
        var notificationId = new NotificationId.CompletionId(completionId);

        var runCommand = Protocol.RunCommandMessage.newBuilder()
                .setName(name)
                .setResultCompletionId(completionId).build();

        var notificationHandle = this.processCompletableCommand(runCommand, CommandAccessor.RUN, new int[]{completionId}, stateContext)[0];

        if (asyncResultsState.nonDeterministicFindId(notificationId)) {
            LOG.trace("Found notification for {} with id {} while replaying, the run closure won't be executed.", notificationHandle, notificationId);
        } else {
LOG.trace(
        "Run notification for {} with id {} not found while replaying, so we enqueue the run to be executed later.",notificationHandle, notificationId
);
runState.insertRunToExecute(notificationHandle);
        }

        return notificationHandle;
    }

    @Override
    public <E extends MessageLite> void processNonCompletableCommand(E commandMessage, CommandAccessor<E> commandAccessor, StateContext stateContext)  {
        stateContext.getJournal().commandTransition(commandAccessor.getName(commandMessage), commandMessage);

        MessageLite actual = takeNextCommandToProcess();
        commandAccessor.checkEntryHeader(commandMessage, actual);

        afterProcessingCommand(stateContext);
    }

    @Override
    public <E extends MessageLite> int[] processCompletableCommand(E commandMessage, CommandAccessor<E> commandAccessor, int[] completionIds, StateContext stateContext) {
        stateContext.getJournal().commandTransition(commandAccessor.getName(commandMessage), commandMessage);
        MessageLite actual = takeNextCommandToProcess();
        commandAccessor.checkEntryHeader(commandMessage, actual);

        int[] handles = new int[completionIds.length];
        for (int i = 0; i < handles.length; i++) {
            handles[i] =  asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionIds[i]));
        }

        afterProcessingCommand(stateContext);

        return handles;
    }

    @Override
    public int processStateGetCommand(String key, StateContext stateContext) {
        var completionId = stateContext.getJournal().nextCompletionNotificationId();
        asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionId));

        stateContext.getJournal().commandTransition("", Protocol.GetEagerStateCommandMessage.getDefaultInstance());
        MessageLite actual = takeNextCommandToProcess();

        if (actual instanceof Protocol.GetEagerStateCommandMessage eagerStateCommandMessage) {
            CommandAccessor.GET_EAGER_STATE.checkEntryHeader(Protocol.GetEagerStateCommandMessage.newBuilder()

                            .setKey(ByteString.copyFromUtf8(key))
                    .build(), actual);
            
            asyncResultsState.insertReady(
                    new NotificationId.CompletionId(completionId),
                    switch (eagerStateCommandMessage.getResultCase()) {
                        case VOID -> NotificationValue.Empty.INSTANCE;
                        case VALUE -> new NotificationValue.Success(
                        Util.byteStringToSlice(
                                eagerStateCommandMessage.getValue().getContent())
                        );
                        case RESULT_NOT_SET -> throw ProtocolException.commandMissingField(Protocol.GetEagerStateCommandMessage.class, "result");
                    }
            );
            
        } else if (actual instanceof Protocol.GetLazyStateCommandMessage) {
            CommandAccessor.GET_LAZY_STATE.checkEntryHeader(Protocol.GetLazyStateCommandMessage.newBuilder()
                    .setKey(ByteString.copyFromUtf8(key))
                            .setResultCompletionId(completionId)
                    .build(), actual);
        } else {
            throw ProtocolException.unexpectedMessage("get state", actual);
        }

        afterProcessingCommand(stateContext);
        
        return completionId;
    }

    @Override
    public int processStateGetKeysCommand( StateContext stateContext) {
        var completionId = stateContext.getJournal().nextCompletionNotificationId();
        asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionId));

        stateContext.getJournal().commandTransition("", Protocol.GetEagerStateKeysCommandMessage.getDefaultInstance());
        MessageLite actual = takeNextCommandToProcess();

        if (actual instanceof Protocol.GetEagerStateKeysCommandMessage eagerStateCommandMessage) {
            CommandAccessor.GET_EAGER_STATE_KEYS.checkEntryHeader(Protocol.GetEagerStateKeysCommandMessage.getDefaultInstance(), actual);

            asyncResultsState.insertReady(
                    new NotificationId.CompletionId(completionId),
                    new NotificationValue.StateKeys(
                            eagerStateCommandMessage.getValue().getKeysList().stream().map(ByteString::toStringUtf8).toList()
                    )
            );
        } else if (actual instanceof Protocol.GetLazyStateKeysCommandMessage) {
            CommandAccessor.GET_LAZY_STATE_KEYS.checkEntryHeader(Protocol.GetLazyStateKeysCommandMessage.newBuilder()
                    .setResultCompletionId(completionId)
                    .build(), actual);
        } else {
            throw ProtocolException.unexpectedMessage("get state keys", actual);
        }

        afterProcessingCommand(stateContext);

        return completionId;
    }

    @Override
    public int createSignalHandle(NotificationId notificationId, StateContext stateContext) {
       return asyncResultsState.createHandleMapping(notificationId);
    }

    private void afterProcessingCommand(StateContext stateContext) {
        if (commandsToProcess.isEmpty()) {
            stateContext.getStateHolder().transition(
                    new ProcessingState(asyncResultsState, runState)
            );
        }
    }

    private MessageLite takeNextCommandToProcess() {
        if (commandsToProcess.isEmpty()) {
            throw ProtocolException.commandsToProcessIsEmpty();
        }
        return commandsToProcess.removeFirst();
    }
    
    @Override
    public InvocationState getInvocationState() {
        return InvocationState.REPLAYING;
    }
}
