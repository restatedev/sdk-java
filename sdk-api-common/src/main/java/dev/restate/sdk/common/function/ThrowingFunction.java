// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.function;

import java.util.function.Function;

/** Like {@link java.util.function.Function} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingFunction<T, R> {
  R apply(T var1) throws Throwable;

  static <T, R> Function<T, R> wrap(ThrowingFunction<T, R> fn) {
    return fn.asFunction();
  }

  default Function<T, R> asFunction() {
    return t -> {
      try {
        return this.apply(t);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      } catch (Throwable e) {
        // Make sure we propagate java.lang.Error
        sneakyThrow(e);
        return null;
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }
}
