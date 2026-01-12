package dev.restate.sdk.internal;

import dev.restate.sdk.Context;

import java.util.Objects;

@org.jetbrains.annotations.ApiStatus.Internal
@org.jetbrains.annotations.ApiStatus.Experimental
public final class ContextThreadLocal {
    public static final ThreadLocal<Context> CONTEXT_THREAD_LOCAL = new ThreadLocal<>();

    public static Context getContext() {
      return Objects.requireNonNull(
          CONTEXT_THREAD_LOCAL.get(),
          "Restate methods must be invoked from within a Restate handler");
    }

    public static void setContext(Context context) {
      CONTEXT_THREAD_LOCAL.set(context);
    }

    public static void clearContext() {
      CONTEXT_THREAD_LOCAL.remove();
    }
}
