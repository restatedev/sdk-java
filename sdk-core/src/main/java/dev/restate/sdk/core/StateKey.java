package dev.restate.sdk.core;

/**
 * This class holds information about state's name and its type tag to be used for serializing and
 * deserializing it.
 *
 * @param <T> the generic type of the state.
 */
public final class StateKey<T> {

  private final String name;
  private final Serde<T> serde;

  private StateKey(String name, Serde<T> serde) {
    this.name = name;
    this.serde = serde;
  }

  /** Create a new {@link StateKey}. */
  public static <T> StateKey<T> of(String name, Serde<T> serde) {
    return new StateKey<>(name, serde);
  }

  /** Create a new {@link StateKey} for {@link String} state. */
  public static StateKey<String> string(String name) {
    return new StateKey<>(name, CoreSerdes.STRING_UTF8);
  }

  /** Create a new {@link StateKey} for bytes state. */
  public static StateKey<byte[]> bytes(String name) {
    return new StateKey<>(name, CoreSerdes.BYTES);
  }

  public String name() {
    return name;
  }

  public Serde<T> serde() {
    return serde;
  }
}
