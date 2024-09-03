// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder
import dev.restate.sdk.testservices.contracts.*

val KNOWN_SERVICES_FACTORIES: Map<String, () -> Any> =
    mapOf(
        AwakeableHolderDefinitions.SERVICE_NAME to { AwakeableHolderImpl() },
        BlockAndWaitWorkflowDefinitions.SERVICE_NAME to { BlockAndWaitWorkflowImpl() },
        CancelTestBlockingServiceDefinitions.SERVICE_NAME to { CancelTestImpl.BlockingService() },
        CancelTestRunnerDefinitions.SERVICE_NAME to { CancelTestImpl.RunnerImpl() },
        CounterDefinitions.SERVICE_NAME to { CounterImpl() },
        FailingDefinitions.SERVICE_NAME to { FailingImpl() },
        KillTestRunnerDefinitions.SERVICE_NAME to { KillTestImpl.RunnerImpl() },
        KillTestSingletonDefinitions.SERVICE_NAME to { KillTestImpl.SingletonImpl() },
        ListObjectDefinitions.SERVICE_NAME to { ListObjectImpl() },
        MapObjectDefinitions.SERVICE_NAME to { MapObjectImpl() },
        NonDeterministicDefinitions.SERVICE_NAME to { NonDeterministicImpl() },
        ProxyDefinitions.SERVICE_NAME to { ProxyImpl() },
        TestUtilsServiceDefinitions.SERVICE_NAME to { TestUtilsServiceImpl() },
    )

val NEEDS_EXPERIMENTAL_CONTEXT: Set<String> = setOf(FailingDefinitions.SERVICE_NAME)

fun main(args: Array<String>) {
  var env = System.getenv("SERVICES")
  if (env == null) {
    env = "*"
  }
  val restateHttpEndpointBuilder = RestateHttpEndpointBuilder.builder()
  if (env == "*") {
    KNOWN_SERVICES_FACTORIES.values.forEach { restateHttpEndpointBuilder.bind(it()) }
  } else {
    for (svc in env.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
      val fqsn = svc.trim { it <= ' ' }
      restateHttpEndpointBuilder.bind(
          KNOWN_SERVICES_FACTORIES[fqsn]?.invoke()
              ?: throw IllegalStateException("Service $fqsn not implemented"))
    }
  }

  val requestSigningKey = System.getenv("E2E_REQUEST_SIGNING")
  if (requestSigningKey != null) {
    restateHttpEndpointBuilder.withRequestIdentityVerifier(
        RestateRequestIdentityVerifier.fromKey(requestSigningKey))
  }

  if (env == "*" || NEEDS_EXPERIMENTAL_CONTEXT.any { env.contains(it) }) {
    restateHttpEndpointBuilder.enablePreviewContext()
  }

  restateHttpEndpointBuilder.buildAndListen()
}
