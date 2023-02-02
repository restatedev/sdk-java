package dev.restate.sdk.blocking;

import dev.restate.generated.core.AwakeableIdentifier;
import dev.restate.sdk.core.TypeTag;
import dev.restate.sdk.core.serde.Serde;
import io.grpc.Status;

import javax.annotation.Nonnull;
import java.time.Duration;

// TODO javadocs
public interface RestateSynchronization {

    /**
     * Causes the current execution of the function invocation to sleep for the given duration.
     *
     * @param duration for which to sleep
     */
    default void sleep(Duration duration) {
        timer(duration).await();
    }

    /**
     * Causes the start of a timer for the given duration. You can await on the timer end by invoking
     * {@link Awaitable#await()}.
     *
     * @param duration for which to sleep
     */
    Awaitable<Void> timer(Duration duration);

    /** Shorthand for {@link #awakeable(TypeTag)} */
    default <T> Awakeable<T> awakeable(Class<T> type) {
        return awakeable(TypeTag.ofClass(type));
    }

    /**
     * Create an {@link Awakeable}, addressable through {@link Awakeable#id()}.
     *
     * <p>You can use this feature to implement external asynchronous systems interactions, for
     * example you can send a Kafka record including the {@link Awakeable#id()}, and then let another
     * service consume from Kafka the responses of given external system interaction by using {@link
     * #completeAwakeable(AwakeableIdentifier, TypeTag, Object)}.
     *
     * @param typeTag the response type tag to use for deserializing
     * @return the result value of the external system interaction
     * @see Awakeable
     */
    <T> Awakeable<T> awakeable(TypeTag<T> typeTag);

    /**
     * Create a new {@link AwakeableHandle} for the provided {@link AwakeableIdentifier}. You can use it to {@link AwakeableHandle#complete(TypeTag, Object)} or {@link AwakeableHandle#fail(Status)} the linked {@link Awakeable}.
     *
     * @see Awakeable
     */
    AwakeableHandle awakeableHandle(AwakeableIdentifier id);

}
