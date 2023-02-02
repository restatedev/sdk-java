package dev.restate.sdk.blocking;

import dev.restate.sdk.core.StateKey;
import dev.restate.sdk.core.serde.Serde;

import javax.annotation.Nonnull;
import java.util.Optional;

// TODO javadocs, explain some properties of the instance storage
public interface RestateInstanceStorage {

    /**
     * Gets the state stored under key, deserializing the raw value using the registered {@link Serde}
     * in the interceptor.
     *
     * @param key identifying the state to get and its type
     * @return an {@link Optional} containing the stored state deserialized or an empty {@link
     *     Optional} if not set yet
     * @throws RuntimeException when the state cannot be deserialized
     */
    <T> Optional<T> get(StateKey<T> key);

    /**
     * Clears the state stored under key.
     *
     * @param key identifying the state to clear
     */
    void remove(StateKey<?> key);

    /**
     * Sets the given value under the given key, serializing the value using the registered {@link
     * Serde} in the interceptor.
     *
     * @param key identifying the value to store and its type
     * @param value to store under the given key. MUST NOT be null.
     */
    <T> void put(StateKey<T> key, @Nonnull T value);

    // TODO get keys https://github.com/restatedev/sdk-java/issues/8
    // TODO methods to create an interface closer to Java's Map:
    //  * computeIfPresent
    //  * computeIfAbsent
    //  * compute
}
