// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.internal

import dev.restate.sdk.kotlin.Context
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine context element that holds the Restate [Context].
 *
 * This element is added to the coroutine context when a handler is invoked, allowing free-floating
 * API functions like `context()`, `run()`, etc. to access the current context from within suspend
 * functions.
 */
internal class RestateContextElement(val ctx: Context) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<RestateContextElement>
}
