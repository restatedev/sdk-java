// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.types;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class is similar to {@link java.util.Optional}, but allows null values.
 *
 * @param <T> the content type
 */
public final class Output<T> {

  private static final Output<?> NOT_READY = new Output<>(false, null);

  private final boolean isReady;
  private final T value;

  private Output(boolean isReady, T value) {
    this.isReady = isReady;
    this.value = value;
  }

  /**
   * @return {@code true} if the output is ready
   */
  public boolean isReady() {
    return isReady;
  }

  /**
   * @return the value
   * @throws IllegalStateException if this output is not ready.
   */
  public T getValue() throws IllegalStateException {
    if (!isReady) {
      throw new IllegalStateException("Not ready");
    }
    return value;
  }

  /**
   * @see Optional#map(Function)
   */
  public <U> Output<U> map(Function<? super T, ? extends U> mapper) {
    Objects.requireNonNull(mapper);
    return isReady ? Output.ready(mapper.apply(value)) : Output.notReady();
  }

  /**
   * @see Optional#orElse(Object)
   */
  public T orElse(T other) {
    return isReady ? value : other;
  }

  /**
   * @see Optional#orElseGet(Supplier)
   */
  public T orElseGet(Supplier<? extends T> supplier) {
    return isReady ? value : supplier.get();
  }

  @SuppressWarnings("unchecked")
  public static <T> Output<T> notReady() {
    return (Output<T>) NOT_READY;
  }

  public static <T> Output<T> ready(T value) {
    return new Output<>(true, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Output<?> output)) return false;
    return isReady == output.isReady && Objects.equals(value, output.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isReady, value);
  }

  @Override
  public String toString() {
    if (isReady) {
      return "EmptyOutput";
    }
    return "Output{" + value + "}";
  }
}
