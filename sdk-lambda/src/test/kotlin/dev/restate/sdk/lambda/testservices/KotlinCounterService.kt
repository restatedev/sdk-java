package dev.restate.sdk.lambda.testservices

import dev.restate.sdk.kotlin.RestateCoroutineService
import kotlinx.coroutines.Dispatchers

class KotlinCounterService :
    KotlinCounterGrpcKt.KotlinCounterCoroutineImplBase(coroutineContext = Dispatchers.Unconfined),
    RestateCoroutineService {

  override suspend fun get(request: CounterRequest): GetResponse {
    val count = (restateContext().get(JavaCounterService.COUNTER) ?: 0) + 1

    throw IllegalStateException("We shouldn't reach this point")
  }
}
