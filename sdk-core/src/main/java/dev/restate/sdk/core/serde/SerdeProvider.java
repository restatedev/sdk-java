package dev.restate.sdk.core.serde;

/** SPI interface for {@link Serde} */
public interface SerdeProvider {

  Serde create();
}
