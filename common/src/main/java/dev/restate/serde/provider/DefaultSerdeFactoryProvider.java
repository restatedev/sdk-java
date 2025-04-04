package dev.restate.serde.provider;

import dev.restate.serde.SerdeFactory;

/**
 * This class is used to autoload either JacksonSerdeFactory or KotlinxSerializationSerdeFactory in the client.
 */
public interface DefaultSerdeFactoryProvider {

    SerdeFactory create();

}
