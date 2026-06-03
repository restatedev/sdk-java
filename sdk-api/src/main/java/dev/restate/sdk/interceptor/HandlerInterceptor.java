// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor;

import dev.restate.sdk.common.AbortedExecutionException;
import dev.restate.sdk.common.HandlerRequest;
import dev.restate.sdk.common.TerminalException;
import dev.restate.sdk.endpoint.HeadersAccessor;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a single handler invocation attempt. Implementations must invoke {@link Next#proceed}
 * exactly once.
 *
 * <p>Typical use is to start a span / observation / MDC scope before {@code proceed()} and finalize
 * it after, e.g.:
 *
 * <pre>{@code
 * (ctx, next) -> {
 *   Span span = tracer.startSpan(...);
 *   try (Scope s = span.makeCurrent()) {
 *     next.proceed();
 *     span.setStatus(OK);
 *   } catch (Exception e) {
 *     span.setStatus(ERROR);
 *     throw e;
 *   } finally {
 *     span.end();
 *   }
 * }
 * }</pre>
 *
 * <h2>Errors visible to the interceptor</h2>
 *
 * The interceptor sees <strong>all</strong> errors except protocol errors occuring during
 * invocation processing, for example:
 *
 * <ul>
 *   <li>Request/Response serialization/deserialization failures.
 *   <li>If the user handler throws, {@code proceed()} rethrows that exception unchanged.
 *   <li>Errors from an innermost interceptor.
 * </ul>
 *
 * <h2>Transforming errors</h2>
 *
 * Interceptors can catch a {@link Exception} from {@code proceed()} and rethrow a different one.
 *
 * <p>A common pattern is to convert known-unrecoverable exceptions (e.g., {@code
 * IllegalArgumentException} from input validation) into {@link TerminalException}.
 *
 * <h2>⚠ Never catch or remap {@link AbortedExecutionException}</h2>
 *
 * {@link AbortedExecutionException} is an internal SDK control-flow signal used to abort the
 * current execution attempt during journal replay and suspension. Remapping it will corrupt the
 * state machine and produce non-deterministic behavior. <b>If you catch it, rethrow it as it
 * is.</b>
 */
@org.jetbrains.annotations.ApiStatus.Experimental
@FunctionalInterface
public interface HandlerInterceptor {
  void aroundHandler(Context context, Next next) throws Exception;

  /** Advances the interceptor chain. Must be called exactly once from {@link #aroundHandler}. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  interface Next {
    void proceed() throws Exception;
  }

  /**
   * Per-invocation context exposed to a {@link HandlerInterceptor}.
   *
   * @param request the user-facing handler request (invocation-level data; constant across retry
   *     attempts).
   * @param attemptHeaders Restate-protocol-level HTTP headers received on the current attempt (e.g.
   *     {@code traceparent}, {@code x-restate-invocation-id}). Differs across retries.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  record Context(HandlerRequest request, HeadersAccessor attemptHeaders) {}

  /**
   * Factory for {@link HandlerInterceptor}.
   *
   * <p>Factories are discovered via {@link java.util.ServiceLoader} (global defaults) or registered
   * explicitly via {@link
   * dev.restate.sdk.HandlerRunner.Options#addHandlerInterceptorFactory(Factory)}.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  @FunctionalInterface
  interface Factory {
    /**
     * Create {@link HandlerInterceptor}. Invoked <strong>once</strong> at endpoint initialization,
     * hence the resulting interceptor is cached and reused across all invocations.
     *
     * <p>Return {@code null} to skip this factory.
     */
    @Nullable HandlerInterceptor createHandlerInterceptor();

    /**
     * Combine multiple factories into a single {@link HandlerInterceptor}.
     *
     * <p>The first is outermost, the last is innermost.
     */
    static HandlerInterceptor combine(Stream<? extends Factory> factories) {
      var resolved =
          factories.map(Factory::createHandlerInterceptor).filter(Objects::nonNull).toList();
      return (ctx, next) -> {
        Next current = next;
        for (int i = resolved.size() - 1; i >= 0; i--) {
          HandlerInterceptor interceptor = resolved.get(i);
          Next inner = current;
          current = () -> interceptor.aroundHandler(ctx, inner);
        }
        current.proceed();
      };
    }

    static HandlerInterceptor combine(Collection<? extends Factory> factories) {
      if (factories.isEmpty()) {
        return combine(Stream.empty());
      }
      return combine(factories.stream());
    }
  }
}
