package dev.restate.sdk.endpoint;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Abstraction for headers map.
 */
public interface HeadersAccessor {
    Iterable<String> keys();

    @Nullable
    String get(String key);

    static HeadersAccessor wrap(Map<String, String> input) {
       return new HeadersAccessor() {
            @Override
            public Iterable<String> keys() {
                return input.keySet();
            }

            @Override
            public String get(String key) {
                for (var k : input.values()) {
                    if (k.equalsIgnoreCase(key)) {
                        return input.get(k);
                    }
                }
                return null;
            }
        };
    }
}
