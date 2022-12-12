package dev.restate.sdk.core.serde.protobuf;

import dev.restate.sdk.core.serde.Serde;
import dev.restate.sdk.core.serde.SerdeProvider;

public class ProtobufSerdeProvider implements SerdeProvider {
  @Override
  public Serde create() {
    return new ProtobufSerde();
  }
}
