// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import dev.restate.sdk.common.StateKey
import dev.restate.serde.kotlinx.typeTag

/**
 * Interface for accessing Virtual Object/Workflow state in the reflection-based API.
 *
 * This interface provides suspend-friendly state operations that can be used from within Restate
 * handlers using the free-floating `state()` function.
 *
 * Example usage:
 * ```kotlin
 * @VirtualObject
 * class Counter {
 *     companion object {
 *         private val COUNT = stateKey<Long>("count")
 *     }
 *
 *     @Handler
 *     suspend fun increment(): Long {
 *         val current = state().get(COUNT) ?: 0L
 *         val next = current + 1
 *         state().set(COUNT, next)
 *         return next
 *     }
 * }
 * ```
 */
interface KotlinState {
  /**
   * Gets the state stored under key, deserializing the raw value using the [StateKey.serdeInfo].
   *
   * @param key identifying the state to get and its type.
   * @return the value containing the stored state deserialized, or null if not set.
   * @throws RuntimeException when the state cannot be deserialized.
   */
  suspend fun <T : Any> get(key: StateKey<T>): T?

  /**
   * Sets the given value under the given key, serializing the value using the [StateKey.serdeInfo].
   *
   * @param key identifying the value to store and its type.
   * @param value to store under the given key.
   * @throws IllegalStateException if called from a Shared handler
   */
  suspend fun <T : Any> set(key: StateKey<T>, value: T)

  /**
   * Clears the state stored under key.
   *
   * @param key identifying the state to clear.
   * @throws IllegalStateException if called from a Shared handler
   */
  suspend fun clear(key: StateKey<*>)

  /**
   * Clears all the state of this virtual object instance key-value state storage.
   *
   * @throws IllegalStateException if called from a Shared handler
   */
  suspend fun clearAll()

  /**
   * Gets all the known state keys for this virtual object instance.
   *
   * @return the immutable collection of known state keys.
   */
  suspend fun stateKeys(): Collection<String>
}

/**
 * Gets the state stored under key.
 *
 * @param key the name of the state key.
 * @return the value containing the stored state deserialized, or null if not set.
 */
suspend inline fun <reified T : Any> KotlinState.get(key: String): T? {
  return this.get(StateKey.of<T>(key, typeTag<T>()))
}

/**
 * Sets the given value under the given key.
 *
 * @param key the name of the state key.
 * @param value to store under the given key.
 * @throws IllegalStateException if called from a Shared handler
 */
suspend inline fun <reified T : Any> KotlinState.set(key: String, value: T) {
  this.set(StateKey.of<T>(key, typeTag<T>()), value)
}
