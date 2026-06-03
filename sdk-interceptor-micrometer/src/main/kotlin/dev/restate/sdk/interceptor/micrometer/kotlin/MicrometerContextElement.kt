// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.interceptor.micrometer.kotlin

import io.micrometer.context.ContextSnapshot
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

/**
 * [ThreadContextElement] that re-applies a [ContextSnapshot] each time the coroutine resumes on a
 * (possibly different) thread.
 *
 * <p>This is what makes Micrometer Observation context (MDC, Brave `CurrentTraceContext`, Reactor
 * `Context`, …) propagate correctly across coroutine suspensions.
 */
internal class MicrometerContextElement(private val snapshot: ContextSnapshot) :
    ThreadContextElement<ContextSnapshot.Scope> {

  companion object Key : CoroutineContext.Key<MicrometerContextElement>

  override val key: CoroutineContext.Key<*> = Key

  override fun updateThreadContext(context: CoroutineContext): ContextSnapshot.Scope =
      snapshot.setThreadLocals()

  override fun restoreThreadContext(
      context: CoroutineContext,
      oldState: ContextSnapshot.Scope,
  ) {
    oldState.close()
  }
}
