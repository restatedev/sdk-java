package dev.restate.sdk.core.serde.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.serde.SerdeProvider;

public class CBORJacksonSerdeProvider implements SerdeProvider {
  @Override
  public Serde create() {
    return JacksonSerde.usingMapper(new ObjectMapper(new CBORFactory()));
  }
}
