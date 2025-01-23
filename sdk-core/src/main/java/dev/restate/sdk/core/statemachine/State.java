// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import com.google.protobuf.MessageLite;
import dev.restate.sdk.types.RetryPolicy;
import dev.restate.sdk.types.Slice;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

sealed interface State {
  void onNewMessage(InvocationInput invocationInput, StateHolder stateHolder, CompletableFuture<Void> waitForReadyFuture);

  void hitError(Throwable throwable, StateHolder stateHolder, Flow.Subscriber<? super MessageLite> outputSubscriber);

  void onInputClosed(StateHolder stateHolder, Flow.Subscriber<? super MessageLite> outputSubscriber);

  StateMachine.DoProgressResponse doProgress(List<Integer> anyHandle, StateHolder stateHolder);

  Optional<NotificationValue> takeNotification(int handle, StateHolder stateHolder);

  StateMachine.@Nullable Input processInputCommand(StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  int processStateGetCommand(String key, StateHolder stateHolder, EagerState eagerState, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  int processStateGetKeysCommand(StateHolder stateHolder, EagerState eagerState, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  void processNonCompletableCommand(MessageLite commandEntry, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  int[] processCompletableCommand(MessageLite commandEntry, int[] ints, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  int createSignalHandle(NotificationId notificationId, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  int processRunCommand(String name, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  void proposeRunCompletion(int handle, Slice value, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  void proposeRunCompletion(int handle, Throwable exception, @Nullable RetryPolicy retryPolicy, StateHolder stateHolder, Journal journal, Flow.Subscriber<? super MessageLite> outputSubscriber);

  void end(StateHolder stateHolder, Flow.Subscriber<? super MessageLite> outputSubscriber);

  InvocationState getInvocationState();
}
