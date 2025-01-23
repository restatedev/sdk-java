package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.types.Slice;
import dev.restate.sdk.types.TerminalException;

import java.util.List;

sealed public interface NotificationValue {

    record Empty() implements NotificationValue {
        public static Empty INSTANCE = new Empty();
    }

    record Success(Slice slice) implements NotificationValue {
    }

    record Failure(TerminalException exception) implements NotificationValue {
    }

    record StateKeys(List<String> stateKeys) implements NotificationValue {
    }

    record InvocationId(String invocationId) implements NotificationValue {
    }
}
