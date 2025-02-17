package dev.restate.sdk;

import dev.restate.serde.Serde;
import dev.restate.serde.SerdeFactory;
import dev.restate.serde.TypeRef;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
public class MySerdeFactory implements SerdeFactory {

    static Serde<String> SERDE = Serde.using("mycontent/type",
            s -> s.toUpperCase().getBytes(),
            b -> new String(b, StandardCharsets.UTF_8).toUpperCase()
            );

    @Override
    public <T> Serde<T> create(TypeRef<T> typeRef) {
        assertThat(typeRef.getType()).isEqualTo(String.class);
        return (Serde<T>) SERDE;
    }

    @Override
    public <T> Serde<T> create(Class<T> clazz) {
        assertThat(clazz).isEqualTo(String.class);
        return (Serde<T>) SERDE;
    }
}
