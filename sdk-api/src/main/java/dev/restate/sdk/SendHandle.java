package dev.restate.sdk;

public final class SendHandle {

    private final Awaitable<String> invocationIdAwaitable;

    SendHandle(Awaitable<String> invocationIdAwaitable) {
        this.invocationIdAwaitable = invocationIdAwaitable;
    }

    public String invocationId() {
        return this.invocationIdAwaitable.await();
    }

}
