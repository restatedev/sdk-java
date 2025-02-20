// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import static dev.restate.sdk.core.statemachine.Util.durationMin;
import static dev.restate.sdk.core.statemachine.Util.sliceToByteString;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import dev.restate.common.Slice;
import dev.restate.sdk.core.ExceptionUtils;
import dev.restate.sdk.core.ProtocolException;
import dev.restate.sdk.core.generated.protocol.Protocol;
import dev.restate.sdk.core.statemachine.StateMachine.DoProgressResponse;
import dev.restate.sdk.types.AbortedExecutionException;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.TerminalException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

final class ProcessingState implements State {

  private static final Logger LOG = LogManager.getLogger(ProcessingState.class);

  private final AsyncResultsState asyncResultsState;
  private final RunState runState;
  private boolean processingFirstEntry;

  ProcessingState(AsyncResultsState asyncResultsState, RunState runState) {
    this.asyncResultsState = asyncResultsState;
    this.runState = runState;
    this.processingFirstEntry = true;
  }

  @Override
  public void onNewMessage(
      InvocationInput invocationInput,
      StateContext stateContext,
      CompletableFuture<Void> waitForReadyFuture) {
    if (invocationInput.header().getType().isNotification()) {
      if (!(invocationInput.message()
          instanceof Protocol.NotificationTemplate notificationTemplate)) {
        throw ProtocolException.unexpectedMessage(
            Protocol.NotificationTemplate.class, invocationInput.message());
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

    Integer maybeRunHandle = runState.tryExecuteRun(awaitingOn);
    if (maybeRunHandle != null) {
      return new DoProgressResponse.ExecuteRun(maybeRunHandle);
    }

    if (stateContext.isInputClosed()) {
      if (runState.anyExecuting(awaitingOn)) {
        return DoProgressResponse.WaitingPendingRun.INSTANCE;
      }

      this.hitSuspended(notificationIds, stateContext);
      ExceptionUtils.sneakyThrow(AbortedExecutionException.INSTANCE);
    }

    return DoProgressResponse.ReadFromInput.INSTANCE;
  }

  @Override
  public boolean isCompleted(int handle) {
    return this.asyncResultsState.isHandleCompleted(handle);
  }

  @Override
  public Optional<NotificationValue> takeNotification(int handle, StateContext stateContext) {
    return this.asyncResultsState.takeHandle(handle);
  }

  @Override
  public int processRunCommand(String name, StateContext stateContext) {
    var completionId = stateContext.getJournal().nextCompletionNotificationId();
    var notificationId = new NotificationId.CompletionId(completionId);

    var runCmdBuilder = Protocol.RunCommandMessage.newBuilder().setResultCompletionId(completionId);
    if (name != null) {
      runCmdBuilder.setName(name);
    }

    var notificationHandle =
        this.processCompletableCommand(
            runCmdBuilder.build(), CommandAccessor.RUN, new int[] {completionId}, stateContext)[0];

    LOG.trace("Enqueued run notification for {} with id {}.", notificationHandle, notificationId);
    runState.insertRunToExecute(notificationHandle);

    return notificationHandle;
  }

  @Override
  public int processStateGetCommand(String key, StateContext stateContext) {
    this.flipFirstProcessingEntry();
    var completionId = stateContext.getJournal().nextCompletionNotificationId();
    var handle =
        asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionId));

    ByteString keyBytes = ByteString.copyFromUtf8(key);
    var eagerStateQuery = stateContext.getEagerState().get(keyBytes);
    if (eagerStateQuery == null) {
      // Lazy state case
      var commandMessage =
          Protocol.GetLazyStateCommandMessage.newBuilder()
              .setKey(keyBytes)
              .setResultCompletionId(completionId)
              .build();
      stateContext
          .getJournal()
          .commandTransition(
              CommandAccessor.GET_LAZY_STATE.getName(commandMessage), commandMessage);
      stateContext.writeMessageOut(commandMessage);

      return handle;
    }

    // Eager state case
    var commandMessageBuilder = Protocol.GetEagerStateCommandMessage.newBuilder().setKey(keyBytes);
    if (eagerStateQuery instanceof NotificationValue.Success) {
      commandMessageBuilder.setValue(
          Protocol.Value.newBuilder()
              .setContent(sliceToByteString(((NotificationValue.Success) eagerStateQuery).slice()))
              .build());
    } else {
      commandMessageBuilder.setVoid(Protocol.Void.getDefaultInstance());
    }
    var commandMessage = commandMessageBuilder.build();
    stateContext
        .getJournal()
        .commandTransition(CommandAccessor.GET_EAGER_STATE.getName(commandMessage), commandMessage);

    asyncResultsState.insertReady(new NotificationId.CompletionId(completionId), eagerStateQuery);
    stateContext.writeMessageOut(commandMessage);

    return handle;
  }

  @Override
  public int processStateGetKeysCommand(StateContext stateContext) {
    this.flipFirstProcessingEntry();
    var completionId = stateContext.getJournal().nextCompletionNotificationId();
    var handle =
        asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionId));

    var eagerStateQuery = stateContext.getEagerState().keys();
    if (eagerStateQuery == null) {
      // Lazy state case
      var commandMessage =
          Protocol.GetLazyStateKeysCommandMessage.newBuilder()
              .setResultCompletionId(completionId)
              .build();
      stateContext
          .getJournal()
          .commandTransition(
              CommandAccessor.GET_LAZY_STATE_KEYS.getName(commandMessage), commandMessage);
      stateContext.writeMessageOut(commandMessage);

      return handle;
    }

    // Eager state case
    var commandMessage =
        Protocol.GetEagerStateKeysCommandMessage.newBuilder()
            .setValue(Protocol.StateKeys.newBuilder().addAllKeys(eagerStateQuery).build())
            .build();
    stateContext
        .getJournal()
        .commandTransition(
            CommandAccessor.GET_EAGER_STATE_KEYS.getName(commandMessage), commandMessage);

    asyncResultsState.insertReady(
        new NotificationId.CompletionId(completionId),
        new NotificationValue.StateKeys(
            eagerStateQuery.stream().map(ByteString::toStringUtf8).toList()));
    stateContext.writeMessageOut(commandMessage);

    return handle;
  }

  @Override
  public <E extends MessageLite> void processNonCompletableCommand(
      E commandMessage, CommandAccessor<E> commandAccessor, StateContext stateContext) {
    stateContext
        .getJournal()
        .commandTransition(commandAccessor.getName(commandMessage), commandMessage);
    this.flipFirstProcessingEntry();

    stateContext.writeMessageOut(commandMessage);
  }

  @Override
  public <E extends MessageLite> int[] processCompletableCommand(
      E commandMessage,
      CommandAccessor<E> commandAccessor,
      int[] completionIds,
      StateContext stateContext) {
    stateContext
        .getJournal()
        .commandTransition(commandAccessor.getName(commandMessage), commandMessage);
    this.flipFirstProcessingEntry();

    stateContext.writeMessageOut(commandMessage);

    int[] handles = new int[completionIds.length];
    for (int i = 0; i < handles.length; i++) {
      handles[i] =
          asyncResultsState.createHandleMapping(new NotificationId.CompletionId(completionIds[i]));
    }

    return handles;
  }

  @Override
  public int createSignalHandle(NotificationId notificationId, StateContext stateContext) {
    return asyncResultsState.createHandleMapping(notificationId);
  }

  @Override
  public void proposeRunCompletion(int handle, Slice value, StateContext stateContext) {
    var notificationId = asyncResultsState.mustResolveNotificationHandle(handle);
    if (!(notificationId instanceof NotificationId.CompletionId)) {
      throw ProtocolException.badRunNotificationId(notificationId);
    }

    runState.notifyExecuted(handle);

    proposeRunCompletion(
        handle,
        Protocol.ProposeRunCompletionMessage.newBuilder()
            .setResultCompletionId(((NotificationId.CompletionId) notificationId).id())
            .setValue(sliceToByteString(value)),
        stateContext);
  }

  @Override
  public void proposeRunCompletion(
      int handle,
      Throwable runException,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy,
      StateContext stateContext) {
    var notificationId = asyncResultsState.mustResolveNotificationHandle(handle);
    if (!(notificationId instanceof NotificationId.CompletionId)) {
      throw ProtocolException.badRunNotificationId(notificationId);
    }

    runState.notifyExecuted(handle);

    TerminalException toWrite;
    if (runException instanceof TerminalException) {
      LOG.trace("The run completed with a terminal exception");
      toWrite = (TerminalException) runException;
    } else {
      toWrite =
          this.rethrowOrConvertToTerminal(runException, attemptDuration, retryPolicy, stateContext);
    }

    proposeRunCompletion(
        handle,
        Protocol.ProposeRunCompletionMessage.newBuilder()
            .setResultCompletionId(((NotificationId.CompletionId) notificationId).id())
            .setFailure(Util.toProtocolFailure(toWrite)),
        stateContext);
  }

  private void proposeRunCompletion(
      int handle,
      Protocol.ProposeRunCompletionMessage.Builder messageBuilder,
      StateContext stateContext) {
    if (!stateContext.maybeWriteMessageOut(messageBuilder.build())) {
      LOG.warn(
          "Cannot write proposed completion for run with handle {} because the output stream was already closed.",
          handle);
    }
  }

  private Duration getDurationSinceLastStoredEntry(StateContext stateContext) {
    // We need to check if this is the first entry we try to commit after replay, and only in this
    // case we need to return the info we got from the start message
    //
    // Moreover, when the retry count is == 0, the durationSinceLastStoredEntry might not be zero.
    // In fact, in that case the duration is the interval between the previously stored entry and
    // the time to start/resume the invocation.
    // For the sake of entry retries though, we're not interested in that time elapsed, so we 0 it
    // here for simplicity of the downstream consumer (the retry policy).
    return this.processingFirstEntry
            && stateContext.getStartInfo().retryCountSinceLastStoredEntry() > 0
        ? stateContext.getStartInfo().durationSinceLastStoredEntry()
        : Duration.ZERO;
  }

  private int getRetryCountSinceLastStoredEntry(StateContext stateContext) {
    // We need to check if this is the first entry we try to commit after replay, and only in this
    // case we need to return the info we got from the start message
    return this.processingFirstEntry
        ? stateContext.getStartInfo().retryCountSinceLastStoredEntry()
        : 0;
  }

  // This function rethrows the exception if a retry needs to happen.
  private TerminalException rethrowOrConvertToTerminal(
      Throwable runException,
      Duration attemptDuration,
      @Nullable RetryPolicy retryPolicy,
      StateContext stateContext) {
    if (retryPolicy == null) {
      LOG.trace("The run completed with an exception and no retry policy was provided");
      // Default behavior is always retry
      ExceptionUtils.sneakyThrow(runException);
    }

    Duration retryLoopDuration =
        this.getDurationSinceLastStoredEntry(stateContext).plus(attemptDuration);
    int retryCount = this.getRetryCountSinceLastStoredEntry(stateContext) + 1;

    if ((retryPolicy.getMaxAttempts() != null && retryPolicy.getMaxAttempts() <= retryCount)
        || (retryPolicy.getMaxDuration() != null
            && retryPolicy.getMaxDuration().compareTo(retryLoopDuration) <= 0)) {
      LOG.trace("The run completed with a retryable exception, but all attempts were exhausted");
      // We need to convert it to TerminalException
      return new TerminalException(runException.toString());
    }

    // Compute next retry delay and throw it!
    Duration nextComputedDelay =
        retryPolicy
            .getInitialDelay()
            .multipliedBy((long) Math.pow(retryPolicy.getExponentiationFactor(), retryCount));
    Duration nextRetryDelay =
        retryPolicy.getMaxDelay() != null
            ? durationMin(retryPolicy.getMaxDelay(), nextComputedDelay)
            : nextComputedDelay;

    this.hitError(runException, nextRetryDelay, stateContext);
    ExceptionUtils.sneakyThrow(runException);
    return null;
  }

  private void flipFirstProcessingEntry() {
    this.processingFirstEntry = false;
  }

  @Override
  public InvocationState getInvocationState() {
    return InvocationState.PROCESSING;
  }
}
