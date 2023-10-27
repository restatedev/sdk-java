package dev.restate.sdk.blocking;

import java.util.function.Supplier;

/** Like {@link Supplier} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingSupplier<T> {

  /**
   * Get a result, potentially throwing an exception.
   *
   * @return a result
   */
  T get() throws Throwable;
}
