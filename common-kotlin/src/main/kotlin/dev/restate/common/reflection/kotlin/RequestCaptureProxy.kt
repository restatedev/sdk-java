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
  suspend fun capture(block: suspend SVC.() -> Any?): CapturedInvocation {
    val proxy =
        ProxySupport.createProxy(clazz) { invocation ->
          throw invocation.captureInvocation(serviceName, key)
        }

    try {
      proxy.block()
    } catch (e: CapturedInvocation) {
      return e
    }

    error(
        "Method invocation was not captured. Make sure to call ONLY a method of the service proxy."
    )
  }
}
