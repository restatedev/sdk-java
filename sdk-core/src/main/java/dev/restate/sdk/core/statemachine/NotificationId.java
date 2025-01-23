package dev.restate.sdk.core.statemachine;

sealed public interface NotificationId {

    record CompletionId(int id) implements NotificationId {
    }

    record SignalId(int id) implements NotificationId {
    }

    record SignalName(String name) implements NotificationId {
    }
}
