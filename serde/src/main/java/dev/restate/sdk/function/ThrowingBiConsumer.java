// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.function;

import java.util.function.BiConsumer;

/** Like {@link BiConsumer} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingBiConsumer<T, U> {
  void accept(T var1, U var2) throws Throwable;

  static <T, U> BiConsumer<T, U> wrap(ThrowingBiConsumer<T, U> fn) {
    return fn.asBiConsumer();
  }

  default BiConsumer<T, U> asBiConsumer() {
    return (t, u) -> {
      try {
        this.accept(t, u);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } catch (Throwable e) {
        // Make sure we propagate java.lang.Error
        sneakyThrow(e);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
