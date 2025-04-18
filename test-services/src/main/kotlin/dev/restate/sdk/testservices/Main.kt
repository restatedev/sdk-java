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
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.endpoint.endpoint
import dev.restate.sdk.testservices.contracts.*

val KNOWN_SERVICES_FACTORIES: Map<String, () -> Any> =
    mapOf(
        AwakeableHolderHandlers.Metadata.SERVICE_NAME to { AwakeableHolderImpl() },
        BlockAndWaitWorkflowHandlers.Metadata.SERVICE_NAME to { BlockAndWaitWorkflowImpl() },
        CancelTestBlockingServiceHandlers.Metadata.SERVICE_NAME to
            {
              CancelTestImpl.BlockingService()
            },
        CancelTestRunnerHandlers.Metadata.SERVICE_NAME to { CancelTestImpl.RunnerImpl() },
        CounterHandlers.Metadata.SERVICE_NAME to { CounterImpl() },
        FailingHandlers.Metadata.SERVICE_NAME to { FailingImpl() },
        KillTestRunnerHandlers.Metadata.SERVICE_NAME to { KillTestImpl.RunnerImpl() },
        KillTestSingletonHandlers.Metadata.SERVICE_NAME to { KillTestImpl.SingletonImpl() },
        ListObjectHandlers.Metadata.SERVICE_NAME to { ListObjectImpl() },
        MapObjectHandlers.Metadata.SERVICE_NAME to { MapObjectImpl() },
        NonDeterministicHandlers.Metadata.SERVICE_NAME to { NonDeterministicImpl() },
        ProxyHandlers.Metadata.SERVICE_NAME to { ProxyImpl() },
        TestUtilsServiceHandlers.Metadata.SERVICE_NAME to { TestUtilsServiceImpl() },
        VirtualObjectCommandInterpreterHandlers.Metadata.SERVICE_NAME to
            {
              VirtualObjectCommandInterpreterImpl()
            },
        interpreterName(0) to { ObjectInterpreterImpl.getInterpreterDefinition(0) },
        interpreterName(1) to { ObjectInterpreterImpl.getInterpreterDefinition(1) },
        interpreterName(2) to { ObjectInterpreterImpl.getInterpreterDefinition(2) },
        ServiceInterpreterHelperHandlers.Metadata.SERVICE_NAME to
            {
              ServiceInterpreterHelperImpl()
            })

val NEEDS_EXPERIMENTAL_CONTEXT: Set<String> = setOf()

fun main(args: Array<String>) {
  var env = System.getenv("SERVICES")
  if (env == null) {
    env = "*"
  }
  val endpoint = endpoint {
    if (env == "*") {
      for (svc in KNOWN_SERVICES_FACTORIES.values) {
        bind(svc())
      }
    } else {
      for (svc in env.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
        val fqsn = svc.trim { it <= ' ' }
        bind(
            KNOWN_SERVICES_FACTORIES[fqsn]?.invoke()
                ?: throw IllegalStateException("Service $fqsn not implemented"))
      }
    }

    val requestSigningKey = System.getenv("E2E_REQUEST_SIGNING")
    if (requestSigningKey != null) {
      withRequestIdentityVerifier(RestateRequestIdentityVerifier.fromKey(requestSigningKey))
    }

    if (env == "*" || NEEDS_EXPERIMENTAL_CONTEXT.any { env.contains(it) }) {
      enablePreviewContext()
    }
  }

  RestateHttpServer.listen(endpoint)
}
