// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.common.reflections.ReflectionUtils.extractServiceName
import dev.restate.sdk.auth.signing.RestateRequestIdentityVerifier
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.endpoint.endpoint
import dev.restate.sdk.testservices.contracts.*

val KNOWN_SERVICES_FACTORIES: Map<String, () -> Any> =
    mapOf(
        extractServiceName(AwakeableHolder::class.java) to { AwakeableHolderImpl() },
        extractServiceName(BlockAndWaitWorkflow::class.java) to { BlockAndWaitWorkflowImpl() },
        extractServiceName(CancelTest.BlockingService::class.java) to
            {
              CancelTestImpl.BlockingService()
            },
        extractServiceName(CancelTest.Runner::class.java) to { CancelTestImpl.RunnerImpl() },
        extractServiceName(Counter::class.java) to { CounterImpl() },
        extractServiceName(Failing::class.java) to { FailingImpl() },
        extractServiceName(KillTest.Runner::class.java) to { KillTestImpl.RunnerImpl() },
        extractServiceName(KillTest.Singleton::class.java) to { KillTestImpl.SingletonImpl() },
        extractServiceName(ListObject::class.java) to { ListObjectImpl() },
        extractServiceName(MapObject::class.java) to { MapObjectImpl() },
        extractServiceName(NonDeterministic::class.java) to { NonDeterministicImpl() },
        extractServiceName(Proxy::class.java) to { ProxyImpl() },
        extractServiceName(TestUtilsService::class.java) to { TestUtilsServiceImpl() },
        extractServiceName(VirtualObjectCommandInterpreter::class.java) to
            {
              VirtualObjectCommandInterpreterImpl()
            },
        interpreterName(0) to { ObjectInterpreterImpl.getInterpreterDefinition(0) },
        interpreterName(1) to { ObjectInterpreterImpl.getInterpreterDefinition(1) },
        interpreterName(2) to { ObjectInterpreterImpl.getInterpreterDefinition(2) },
        extractServiceName(ServiceInterpreterHelper::class.java) to
            {
              ServiceInterpreterHelperImpl()
            },
    )

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
                ?: throw IllegalStateException("Service $fqsn not implemented")
        )
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
