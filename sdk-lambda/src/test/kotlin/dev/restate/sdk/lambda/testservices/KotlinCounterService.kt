package dev.restate.sdk.lambda.testservices

import RestateCoroutineService

class KotlinCounterService :
    KotlinCounterGrpcKt.KotlinCounterCoroutineImplBase(), RestateCoroutineService {

  override suspend fun get(request: CounterRequest): GetResponse {
    val count = (restateContext().get(JavaCounterService.COUNTER) ?: 0) + 1

    throw IllegalStateException("We shouldn't reach this point")
  }
}
