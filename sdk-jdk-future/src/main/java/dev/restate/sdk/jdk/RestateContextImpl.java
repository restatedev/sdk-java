package dev.restate.sdk.jdk;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.syscalls.Syscalls;
import java.util.concurrent.CompletionStage;

public class RestateContextImpl implements RestateContext {

  private final Syscalls syscalls;

  RestateContextImpl(Syscalls syscalls) {
    this.syscalls = syscalls;
  }

  @Override
  public <T> CompletionStage<T> get(StateKey<T> stateKey) {
    return null;
  }
}
