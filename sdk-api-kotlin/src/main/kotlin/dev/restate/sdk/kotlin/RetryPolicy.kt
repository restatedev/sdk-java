// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Retry policy configuration. */
data class RetryPolicy(
    /** Initial retry delay for the first retry attempt. */
    val initialDelay: Duration,
    /** Exponentiation factor to use when computing the next retry delay. */
    val exponentiationFactor: Float,
    /** Maximum delay between retries. */
    val maxDelay: Duration? = null,
    /**
     * Maximum number of attempts before giving up retrying.
     *
     * The policy gives up retrying when either at least the given number of attempts is reached, or
     * the [maxDuration] (if set) is reached first. If both [maxAttempts] and [maxDuration] are
     * `null`, the policy will retry indefinitely.
     *
     * **Note:** The number of actual retries may be higher than the provided value. This is due to
     * the nature of the `run` operation, which executes the closure on the service and sends the
     * result afterward to Restate.
     */
    val maxAttempts: Int? = null,
    /**
     * Maximum duration of the retry loop.
     *
     * The policy gives up retrying when either the retry loop lasted at least for this given max
     * duration, or the [maxAttempts] (if set) is reached first. If both [maxAttempts] and
     * [maxDuration] are `null`, the policy will retry indefinitely.
     *
     * **Note:** The real retry loop duration may be higher than the given duration. TThis is due to
     * the nature of the `run` operation, which executes the closure on the service and sends the
     * result afterward to Restate.
     */
    val maxDuration: Duration? = null
) {

  data class Builder(
      var initialDelay: Duration = 100.milliseconds,
      var exponentiationFactor: Float = 2.0f,
      var maxDelay: Duration? = null,
      var maxAttempts: Int? = null,
      var maxDuration: Duration? = null
  ) {
    fun build() =
        RetryPolicy(
            initialDelay = initialDelay,
            exponentiationFactor = exponentiationFactor,
            maxDelay = maxDelay,
            maxDuration = maxDuration,
            maxAttempts = maxAttempts)
  }
}

fun retryPolicy(init: RetryPolicy.Builder.() -> Unit): RetryPolicy {
  val builder = RetryPolicy.Builder()
  builder.init()
  return builder.build()
}
