package dev.restate.sdk.core.function;

/** Like {@link java.util.function.Supplier} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingSupplier<T> {

  /**
   * Get a result, potentially throwing an exception.
   *
   * @return a result
   */
  T get() throws Throwable;
}
