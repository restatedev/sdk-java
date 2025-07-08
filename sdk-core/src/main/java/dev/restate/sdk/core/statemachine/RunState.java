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
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class RunState {
  private final Map<Integer, Run> runs = new HashMap<>();

  public void insertRunToExecute(int handle, int commandIndex, String commandName) {
    runs.put(handle, new Run(commandIndex, commandName, RunStateInner.ToExecute));
  }

  public @Nullable Integer tryExecuteRun(Collection<Integer> anyHandle) {
    for (Map.Entry<Integer, Run> entry : runs.entrySet()) {
      Integer handle = entry.getKey();
      Run run = entry.getValue();
      if (run.state == RunStateInner.ToExecute && anyHandle.contains(handle)) {
        entry.setValue(new Run(run.commandIndex, run.commandName, RunStateInner.Executing));
        return handle;
      }
    }
    return null;
  }

  public boolean anyExecuting(Collection<Integer> anyHandle) {
    return anyHandle.stream()
        .anyMatch(h -> runs.containsKey(h) && runs.get(h).state == RunStateInner.Executing);
  }

  /**
   * Notifies that execution has completed for the given handle.
   *
   * @param executed the handle of the completed execution
   * @return a tuple of (commandName, commandIndex)
   */
  public CommandInfo notifyExecutionCompleted(int executed) {
    Run run = runs.remove(executed);
    if (run == null) {
      throw new IllegalStateException("There must be a corresponding run for the given handle");
    }
    return new CommandInfo(run.commandName, run.commandIndex);
  }

  enum RunStateInner {
    ToExecute,
    Executing
  }

  record Run(int commandIndex, String commandName, RunStateInner state) {}

  /**
   * Represents the command information in the order expected by the Rust code (commandName,
   * commandIndex).
   */
  record CommandInfo(String commandName, int commandIndex) {}
}
