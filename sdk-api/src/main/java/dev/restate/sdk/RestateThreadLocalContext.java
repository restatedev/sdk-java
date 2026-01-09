// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.function.ThrowingSupplier;
import java.util.Objects;

final class RestateThreadLocalContext {

  static final ThreadLocal<Context> CONTEXT_THREAD_LOCAL = new ThreadLocal<>();

  static Context getContext() {
    return Objects.requireNonNull(
        CONTEXT_THREAD_LOCAL.get(),
        "Restate methods must be invoked from within a Restate handler");
  }

  static <T> T wrap(Context context, ThrowingSupplier<T> runnable) throws Throwable {
    setContext(context);
    try {
      return runnable.get();
    } finally {
      clearContext();
    }
  }

  static void setContext(Context context) {
    CONTEXT_THREAD_LOCAL.set(context);
  }

  static void clearContext() {
    CONTEXT_THREAD_LOCAL.remove();
  }
}
