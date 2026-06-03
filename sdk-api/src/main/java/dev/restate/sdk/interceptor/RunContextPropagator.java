// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor;

import dev.restate.sdk.HandlerRunner;
import dev.restate.sdk.Restate;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Propagates thread-local state from the thread running the handler code to the thread executing a
 * {@link Restate#run} closure.
 *
 * <p>{@link Restate#run} closures are scheduled on the Executor provided in {@link
 * HandlerRunner.Options#setExecutor(Executor)}. Thread-local state installed on the handler thread
 * (e.g. by a {@link HandlerInterceptor} opening a tracing span / observation scope, MDC values,
 * security context, …) is not visible there by default. Implementations of this interface capture
 * that state at submission time and restore it around the closure execution.
 *
 * <p>{@link #capture} is invoked on the handler thread (or whatever thread calls {@link
 * Restate#run}) at submission time; {@link CapturedContext#wrap} is invoked later, and the
 * resulting {@link Runnable} executes on the worker thread. The classic shape is:
 *
 * <pre>{@code
 * () -> {
 *   var captured = captureCurrentThreadState();
 *   return runnable -> () -> {
 *     try (var scope = captured.install()) {
 *       runnable.run();
 *     }
 *   };
 * }
 * }</pre>
 *
 * <p>Propagators are discovered via {@link java.util.ServiceLoader} (global defaults) or registered
 * explicitly via {@link
 * dev.restate.sdk.HandlerRunner.Options#addRunContextPropagator(RunContextPropagator)}. They apply
 * to every {@link Restate#run} execution, including retries.
 */
@org.jetbrains.annotations.ApiStatus.Experimental
@FunctionalInterface
public interface RunContextPropagator {

  /** Capture a thread-local state from the calling thread. */
  CapturedContext capture();

  /** Thread-local state captured by {@link #capture}. */
  @org.jetbrains.annotations.ApiStatus.Experimental
  @FunctionalInterface
  interface CapturedContext {
    /**
     * Wrap {@code runnable} so the captured state is re-installed around it when executed on
     * another thread.
     */
    Runnable wrap(Runnable runnable);
  }

  /**
   * Combine multiple propagators into a single {@link RunContextPropagator}.
   *
   * <p>{@link #capture} captures with all propagators in registration order; at wrap time the first
   * propagator restores outermost.
   */
  static RunContextPropagator combine(Stream<? extends RunContextPropagator> propagators) {
    List<? extends RunContextPropagator> resolved = propagators.toList();
    if (resolved.isEmpty()) {
      return () -> runnable -> runnable;
    }
    return () -> {
      // Capture with all propagators now, on the submitting thread.
      List<CapturedContext> captured =
          resolved.stream().map(RunContextPropagator::capture).toList();
      return runnable -> {
        Runnable wrapped = runnable;
        // Wrap right-to-left so the first registered propagator restores outermost.
        for (int i = captured.size() - 1; i >= 0; i--) {
          wrapped = captured.get(i).wrap(wrapped);
        }
        return wrapped;
      };
    };
  }

  static RunContextPropagator combine(Collection<? extends RunContextPropagator> propagators) {
    if (propagators.isEmpty()) {
      return combine(Stream.empty());
    }
    return combine(propagators.stream());
  }
}
