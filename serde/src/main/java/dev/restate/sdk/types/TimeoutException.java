package dev.restate.sdk.types;

public class TimeoutException extends TerminalException {

    public TimeoutException(String message) {
        super(409, message);
    }
}
