package dev.restate.sdk.core;

/**
 * This class holds information about state's name and its type tag to be used for serializing and
 * deserializing it.
 *
 * @param <T> the generic type of the state.
 */
public final class StateKey<T> {

  private final String name;
  private final TypeTag<T> typeTag;

  private StateKey(String name, TypeTag<T> typeTag) {
    this.name = name;
    this.typeTag = typeTag;
  }

  /** Shorthand for {@link #of(String, TypeTag)}. */
  public static <T> StateKey<T> of(String name, Class<T> type) {
    return of(name, TypeTag.ofClass(type));
  }

  /** Factory method to create a new instance of this class. */
  public static <T> StateKey<T> of(String name, TypeTag<T> typeTag) {
    return new StateKey<>(name, typeTag);
  }

  public String name() {
    return name;
  }

  public TypeTag<T> typeTag() {
    return typeTag;
  }
}
