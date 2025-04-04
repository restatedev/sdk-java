package dev.restate.serde.kotlinx;

import dev.restate.serde.provider.DefaultSerdeFactoryProvider

public class KotlinSerializationSerdeFactoryProvider: DefaultSerdeFactoryProvider  {
  override fun create() = KotlinSerializationSerdeFactory()
}
