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
import dev.restate.sdk.core.EndpointRequestHandler;
import java.util.Objects;
import java.util.concurrent.Flow;

final class StateContext {

  private final StateHolder stateHolder;
  private final Journal journal;
  private EagerState eagerState;
  private transient StartInfo startInfo;
  private boolean inputClosed;
  private Flow.Subscriber<MessageLite> outputSubscriber;

  StateContext(EndpointRequestHandler.LoggingContextSetter loggingContextSetter) {
    this.stateHolder = new StateHolder(loggingContextSetter);
    this.journal = new Journal();
    this.inputClosed = false;
  }

  public State getCurrentState() {
    return stateHolder.getState();
  }

  public StateHolder getStateHolder() {
    return stateHolder;
  }

  public Journal getJournal() {
    return journal;
  }

  public StateContext setEagerState(EagerState eagerState) {
    this.eagerState = eagerState;
    return this;
  }

  public StateContext setStartInfo(StartInfo startInfo) {
    this.startInfo = startInfo;
    return this;
  }

  EagerState getEagerState() {
    return Objects.requireNonNull(eagerState, "The state machine should be initialized");
  }

  StartInfo getStartInfo() {
    return Objects.requireNonNull(startInfo, "The state machine should be initialized");
  }

  public void markInputClosed() {
    this.inputClosed = true;
  }

  public boolean isInputClosed() {
    return this.inputClosed;
  }

  public void writeMessageOut(MessageLite msg) {
    Objects.requireNonNull(
            this.outputSubscriber,
            "Output subscriber should be configured before running the state machine")
        .onNext(msg);
  }

  public boolean maybeWriteMessageOut(MessageLite msg) {
    if (this.outputSubscriber != null) {
      this.outputSubscriber.onNext(msg);
      return true;
    }
    return false;
  }

  public void closeOutputSubscriber() {
    if (this.outputSubscriber != null) {
      this.outputSubscriber.onComplete();
      this.outputSubscriber = null;
    }
  }

  public void registerOutputSubscriber(Flow.Subscriber<MessageLite> outputSubscriber) {
    this.outputSubscriber = outputSubscriber;
  }
}
