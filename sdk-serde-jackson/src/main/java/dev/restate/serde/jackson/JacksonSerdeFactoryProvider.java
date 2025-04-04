package dev.restate.serde.jackson;

import dev.restate.serde.SerdeFactory;
import dev.restate.serde.provider.DefaultSerdeFactoryProvider;

public class JacksonSerdeFactoryProvider implements DefaultSerdeFactoryProvider {
    @Override
    public SerdeFactory create() {
        return new JacksonSerdeFactory();
    }
}
