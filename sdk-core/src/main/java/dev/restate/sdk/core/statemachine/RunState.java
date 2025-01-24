package dev.restate.sdk.core.statemachine;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

final class RunState {
    private final Set<Integer> toExecute = new HashSet<>();
    private final Set<Integer> executing = new HashSet<>();

    public void insertRunToExecute(int handle) {
        toExecute.add(handle);
    }

    public @Nullable Integer tryExecuteRun(Set<Integer> anyHandle) {
        for (int runnable : toExecute) {
            if (anyHandle.contains(runnable)) {
                toExecute.remove(runnable);
                executing.add(runnable);
                return runnable;
            }
        }
        return null;
    }

    public boolean anyExecuting(int[] anyHandle) {
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