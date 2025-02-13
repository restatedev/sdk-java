package dev.restate.sdk;

import dev.restate.common.function.ThrowingFunction;
import dev.restate.sdk.endpoint.definition.AsyncResult;
import dev.restate.sdk.endpoint.definition.HandlerContext;
import dev.restate.sdk.types.TerminalException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public final class Select<T> extends Awaitable<T> {

    private final List<Awaitable<?>> awaitables;
    private AsyncResult<T> asyncResult;

    Select() {
        this.awaitables = new ArrayList<>();
    }

    public static<T> Select<T> select() {
        return new Select<>();
    }

    public Select<T> or(
            Awaitable<T> awaitable
    ) {
        this.awaitables.add(awaitable);
        this.asyncResult = null;
        return this;
    }

    public <U> Select<T> when(
            Awaitable<U> awaitable,
            ThrowingFunction<U, T> successMapper
    ) {
        this.awaitables.add(awaitable.map(successMapper));
        this.asyncResult = null;
        return this;
    }

    public <U> Select<T> when(Awaitable<U> awaitable, ThrowingFunction<U, T> successMapper, ThrowingFunction<TerminalException, T> failureMapper) {
        this.awaitables.add(awaitable.map(successMapper, failureMapper));
        this.asyncResult = null;
        return this;
    }

    @Override
    protected AsyncResult<T> asyncResult() {
        if (this.asyncResult == null) {
            recreateAsyncResult();
        }
        return this.asyncResult;
    }

    @Override
    protected Executor serviceExecutor() {
       checkNonEmpty();
        return awaitables.get(0).serviceExecutor();
    }

    private void checkNonEmpty() {
        if (awaitables.isEmpty()) {
            throw new IllegalArgumentException("Select is empty");
        }
    }

    private void recreateAsyncResult() {
        checkNonEmpty();
        List<Awaitable<?>> awaitables = List.copyOf(this.awaitables);
        List<AsyncResult<?>> ars = awaitables.stream()
                .map(Awaitable::asyncResult)
                .collect(Collectors.toList());
        HandlerContext ctx = ars.get(0).ctx();
        //noinspection unchecked
        this.asyncResult = ctx.createAnyAsyncResult(ars).map(i -> (CompletableFuture<T>) ars.get(i).poll());
    }
}
