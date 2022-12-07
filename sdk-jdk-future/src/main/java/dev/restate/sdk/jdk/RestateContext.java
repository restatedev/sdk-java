package dev.restate.sdk.jdk;

import dev.restate.sdk.core.StateKey;
import java.util.concurrent.CompletionStage;

public interface RestateContext {

  <T> CompletionStage<T> get(StateKey<T> stateKey);
}
