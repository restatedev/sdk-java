// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor;

import dev.restate.sdk.Restate;
import dev.restate.sdk.common.HandlerRequest;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Wraps the execution of a {@link Restate#run} closure. Implementations must invoke {@link
 * Next#proceed} exactly once.
 *
 * <p>Only invoked when the run closure actually executes; replayed runs are skipped by the runner,
 * so this interceptor is never called during replay.
 *
 * <h2>Errors visible to the interceptor</h2>
 *
 * The interceptor sees user code throwables and serialization failures unchanged. Whatever the
 * outermost interceptor rethrows is what Restate's retry machinery sees.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
@FunctionalInterface
public interface RunInterceptor {
  void aroundRun(Context context, Next next) throws Exception;

  /** Advances the interceptor chain. Must be called exactly once from {@link #aroundRun}. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  interface Next {
    void proceed() throws Exception;
  }

  /** Per-{@code ctx.run} call context exposed to a {@link RunInterceptor}. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  record Context(HandlerRequest request, @Nullable String runName) {}

  /**
   * Factory for {@link RunInterceptor}.
   *
   * <p>Factories are discovered via {@link java.util.ServiceLoader} (global defaults) or registered
   * explicitly via {@link
   * dev.restate.sdk.HandlerRunner.Options#addRunInterceptorFactory(RunInterceptor.Factory)}.
   */
  @org.jetbrains.annotations.ApiStatus.Experimental
  @FunctionalInterface
  interface Factory {
    /**
     * Create {@link RunInterceptor}. Invoked <strong>once</strong> at endpoint initialization,
     * hence the resulting interceptor is cached and reused across all invocations.
     *
     * <p>Return {@code null} to skip this factory.
     */
    @Nullable RunInterceptor createRunInterceptor();

    /**
     * Combine multiple factories into a single {@link RunInterceptor}.
     *
     * <p>The first is outermost, the last is innermost.
     */
    static RunInterceptor combine(Stream<? extends Factory> factories) {
      var resolved = factories.map(Factory::createRunInterceptor).filter(Objects::nonNull).toList();
      return (runCtx, next) -> {
        Next current = next;
        for (int i = resolved.size() - 1; i >= 0; i--) {
          RunInterceptor interceptor = resolved.get(i);
          Next inner = current;
          current = () -> interceptor.aroundRun(runCtx, inner);
        }
        current.proceed();
      };
    }

    static RunInterceptor combine(Collection<? extends Factory> factories) {
      if (factories.isEmpty()) {
        return combine(Stream.empty());
      }
      return combine(factories.stream());
    }
  }
}
