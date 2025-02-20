// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class RunState {
  private final Set<Integer> toExecute = new HashSet<>();
  private final Set<Integer> executing = new HashSet<>();

  public void insertRunToExecute(int handle) {
    toExecute.add(handle);
  }

  public @Nullable Integer tryExecuteRun(Collection<Integer> anyHandle) {
    for (int maybeRun : anyHandle) {
      if (toExecute.contains(maybeRun)) {
        toExecute.remove(maybeRun);
        executing.add(maybeRun);
        return maybeRun;
      }
    }
    return null;
  }

  public boolean anyExecuting(Collection<Integer> anyHandle) {
    for (int handle : anyHandle) {
      if (executing.contains(handle)) {
        return true;
      }
    }
    return false;
  }

  public void notifyExecuted(int executed) {
    toExecute.remove(executed);
    executing.remove(executed);
  }
}
