package dev.restate.sdk.fake;

import dev.restate.common.function.ThrowingRunnable;
import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.ContextInternal;
import dev.restate.sdk.endpoint.definition.HandlerRunner;
import dev.restate.sdk.internal.ContextThreadLocal;

/**
 * Fake Restate environment for testing handlers using the new reflection API.
 *
 * <p>This class provides utility methods to execute service methods that use the new reflection
 * API (without explicit Context parameters) in a fake Restate context for testing purposes.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Test
 * public void testGreeter() {
 *     GreeterService greeter = new GreeterService();
 *
 *     // Execute the service method in a fake Restate context
 *     String response = FakeRestate.execute(() -> greeter.greet(new Greeting("Francesco")));
 *
 *     assertEquals("You said hi to Francesco!", response);
 * }
 * }</pre>
 *
 * <p>For advanced scenarios, you can customize the context behavior using {@link
 * ContextExpectations}:
 *
 * <pre>{@code
 * @Test
 * public void testWithExpectations() {
 *     GreeterService greeter = new GreeterService();
 *
 *     ContextExpectations expectations = new ContextExpectations()
 *         .withRandom(new Random(42));
 *
 *     String response = FakeRestate.execute(expectations, () -> greeter.greet(new Greeting("Alice")));
 *
 *     assertEquals("Expected response", response);
 * }
 * }</pre>
 */
@org.jetbrains.annotations.ApiStatus.Experimental
public final class FakeRestate {

    /**
     * Execute a runnable in a fake Restate context with default expectations.
     *
     * @param runnable the code to execute
     */
    public static void execute(ThrowingRunnable runnable) {
        execute(new ContextExpectations(), runnable);
    }

    /**
     * Execute a runnable in a fake Restate context with custom expectations.
     *
     * @param expectations the context expectations to use
     * @param runnable the code to execute
     */
    public static void execute(ContextExpectations expectations, ThrowingRunnable runnable) {
        var fakeHandlerContext = new FakeHandlerContext(expectations);
        var fakeContext =
                ContextInternal.createContext(
                        fakeHandlerContext, Runnable::run, expectations.serdeFactory());
        HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.set(fakeHandlerContext);
        ContextThreadLocal.setContext(fakeContext);
        try {
runnable.run();
        } catch (Throwable e) {
            sneakyThrow(e);
        } finally {
            ContextThreadLocal.clearContext();
            HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.remove();
        }
    }

    /**
     * Execute a supplier in a fake Restate context with default expectations and return the result.
     *
     * @param runnable the code to execute
     * @param <T> the return type
     * @return the result of the supplier
     */
    public static <T> T execute( ThrowingSupplier<T> runnable) {
        return execute(new ContextExpectations(), runnable);
    }

    /**
     * Execute a supplier in a fake Restate context with custom expectations and return the result.
     *
     * @param expectations the context expectations to use
     * @param runnable the code to execute
     * @param <T> the return type
     * @return the result of the supplier
     */
    public static <T> T execute(ContextExpectations expectations, ThrowingSupplier<T> runnable) {
        var fakeHandlerContext = new FakeHandlerContext(expectations);
        var fakeContext =
                ContextInternal.createContext(
                        fakeHandlerContext, Runnable::run, expectations.serdeFactory());
        HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.set(fakeHandlerContext);
        ContextThreadLocal.setContext(fakeContext);
        try {
            return runnable.get();
        } catch (Throwable e) {
            sneakyThrow(e);
            return null;
        } finally {
            ContextThreadLocal.clearContext();
            HandlerRunner.HANDLER_CONTEXT_THREAD_LOCAL.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }
}
