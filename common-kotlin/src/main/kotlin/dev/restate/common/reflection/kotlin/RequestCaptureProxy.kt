// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflection.kotlin

import dev.restate.common.reflections.ProxySupport
import dev.restate.common.reflections.ReflectionUtils
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine

/**
 * Captures method invocations on a proxy to extract invocation information.
 *
 * This class is used to intercept calls on service proxies and extract the method metadata and
 * arguments without actually executing the method. The captured information can then be used to
 * build requests for remote invocation.
 *
 * @param SVC the service type
 * @property clazz the service class
 * @property serviceName the resolved service name
 * @property key the virtual object/workflow key (null for stateless services)
 */
class RequestCaptureProxy<SVC : Any>(private val clazz: Class<SVC>, private val key: String?) {

  private val serviceName: String = ReflectionUtils.extractServiceName(clazz)

  /**
   * Capture a method invocation from the given block.
   *
   * @param block the suspend lambda that invokes a method on the service proxy
   * @return the captured invocation information
   */
  fun capture(block: suspend (SVC) -> Any?): CapturedInvocation {
    var capturedInvocation: CapturedInvocation? = null

    val proxy =
        ProxySupport.createProxy(clazz) { invocation ->
          capturedInvocation = invocation.captureInvocation(serviceName, key)

          // Return COROUTINE_SUSPENDED to prevent actual execution
          COROUTINE_SUSPENDED
        }

    // Invoke the block with the proxy to capture the method call.
    // Since the proxy returns COROUTINE_SUSPENDED, we use startCoroutine
    // which starts but doesn't block waiting for completion.
    val capturingContinuation =
        object : Continuation<Any?> {
          override val context = EmptyCoroutineContext

          override fun resumeWith(result: Result<Any?>) {
            // Do nothing - we're just capturing, the coroutine suspends immediately
          }
        }

    val suspendBlock: suspend () -> Any? = { block(proxy) }
    suspendBlock.startCoroutine(capturingContinuation)

    return capturedInvocation
        ?: error(
            "Method invocation was not captured. Make sure to call ONLY a method of the service proxy."
        )
  }
}
