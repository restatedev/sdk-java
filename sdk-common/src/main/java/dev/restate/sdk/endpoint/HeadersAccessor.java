package dev.restate.sdk.endpoint;

import org.jspecify.annotations.Nullable;

/**
 * Abstraction for headers map.
 */
public interface HeadersAccessor {
    Iterable<String> keys();

    @Nullable
    String get(String key);
}
