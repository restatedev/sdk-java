// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.interceptor

import dev.restate.sdk.common.AbortedExecutionException
import dev.restate.sdk.common.HandlerRequest
import dev.restate.sdk.common.TerminalException
import dev.restate.sdk.endpoint.HeadersAccessor
import dev.restate.sdk.kotlin.runBlock

/**
 * Wraps a single handler invocation attempt for Kotlin coroutine handlers. Implementations must
 * call `next()` exactly once.
 *
 * ## Errors visible to the interceptor
 *
 * The interceptor sees **all** errors except protocol errors occurring during invocation
 * processing, for example:
 * - Request/Response serialization/deserialization failures.
 * - If the user handler throws, `next()` rethrows that exception unchanged.
 * - Errors from an innermost interceptor.
 *
 * ## Transforming errors
 *
 * Interceptors can catch an [Exception] from `next()` and rethrow a different one.
 *
 * A common pattern is to convert known-unrecoverable exceptions (e.g. [IllegalArgumentException]
 * from input validation) into [TerminalException].
 *
 * ## ⚠ Never catch or remap [AbortedExecutionException]
 *
 * [AbortedExecutionException] is an internal SDK control-flow signal used to abort the current
 * execution attempt during journal replay and suspension. Remapping it will corrupt the state
 * machine and produce non-deterministic behavior. **If you catch it, rethrow it as it is.**
 */
fun interface HandlerInterceptor {
  suspend fun aroundHandler(context: Context, next: suspend () -> Unit)

  /**
   * Per-invocation context exposed to a [HandlerInterceptor].
   *
   * @property request the user-facing handler request (invocation-level data; constant across retry
   *   attempts).
   * @property attemptHeaders Restate-protocol-level HTTP headers received on the current attempt
   *   (e.g. `traceparent`, `x-restate-invocation-id`). Differs across retries. Unmodifiable.
   */
  data class Context(val request: HandlerRequest, val attemptHeaders: HeadersAccessor)

  /**
   * Factory for [HandlerInterceptor].
   *
   * Factories are discovered via SPI or registered explicitly via
   * [dev.restate.sdk.kotlin.HandlerRunner.Options.handlerInterceptorFactories].
   */
  fun interface Factory {
    /**
     * Create [HandlerInterceptor].
     *
     * Invoked **once** at endpoint initialization; the resulting interceptor is reused across every
     * invocation.
     *
     * Return {@code null} to skip this factory.
     */
    fun createHandlerInterceptor(): HandlerInterceptor?

    companion object {
      /**
       * Combine multiple factories into a single [HandlerInterceptor].
       *
       * Factories are resolved **eagerly**, nulls are skipped, and the resulting interceptors are
       * folded around `next` on each invocation — the first registered factory wraps outermost.
       */
      fun combine(factories: Iterable<Factory>): HandlerInterceptor {
        val resolved = factories.mapNotNull { it.createHandlerInterceptor() }
        return HandlerInterceptor { ctx, next ->
          var current: suspend () -> Unit = next
          for (i in resolved.indices.reversed()) {
            val interceptor = resolved[i]
            val inner = current
            current = { interceptor.aroundHandler(ctx, inner) }
          }
          current()
        }
      }
    }
  }
}

/**
 * Wraps the execution of a [runBlock] closure. Implementations must invoke `next()` exactly once.
 *
 * Only invoked when the run closure actually executes; replayed runs are skipped by the runner, so
 * this interceptor is never called during replay.
 *
 * ## Errors visible to the interceptor
 *
 * The interceptor sees user code throwables and serialization failures unchanged. Whatever the
 * outermost interceptor rethrows is what Restate's retry machinery sees.
 */
fun interface RunInterceptor {
  suspend fun aroundRun(context: Context, next: suspend () -> Unit)

  /** Per-`ctx.run` call context exposed to a [RunInterceptor]. */
  data class Context(val request: HandlerRequest, val runName: String?)

  /**
   * Factory for [RunInterceptor].
   *
   * Factories are discovered via SPI or registered explicitly via
   * [dev.restate.sdk.kotlin.HandlerRunner.Options.runInterceptorFactories].
   */
  fun interface Factory {
    /**
     * Create [RunInterceptor].
     *
     * Invoked **once** at endpoint initialization; the resulting interceptor is reused across every
     * [runBlock] in every invocation.
     *
     * Return {@code null} to skip this factory.
     */
    fun createRunInterceptor(): RunInterceptor?

    companion object {
      /** Combine multiple factories into a single [RunInterceptor]. */
      fun combine(factories: Iterable<Factory>): RunInterceptor {
        val resolved = factories.mapNotNull { it.createRunInterceptor() }
        return RunInterceptor { runCtx, next ->
          var current: suspend () -> Unit = next
          for (i in resolved.indices.reversed()) {
            val interceptor = resolved[i]
            val inner = current
            current = { interceptor.aroundRun(runCtx, inner) }
          }
          current()
        }
      }
    }
  }
}
