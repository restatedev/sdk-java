import com.google.protobuf.Empty
import dev.restate.sdk.core.StateKey
import dev.restate.sdk.examples.BlockingCounter
import dev.restate.sdk.examples.generated.*
import dev.restate.sdk.vertx.RestateHttpServerBuilder
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.dispatcher
import kotlin.coroutines.CoroutineContext
import org.apache.logging.log4j.LogManager

class CounterKt(coroutineContext: CoroutineContext) :
    CounterGrpcKt.CounterCoroutineImplBase(coroutineContext), RestateCoroutineService {

  private val LOG = LogManager.getLogger(BlockingCounter::class.java)

  private val TOTAL = StateKey.of("total", Long::class.java)

  override suspend fun reset(request: CounterRequest): Empty {
    restateContext().clear(TOTAL)
    return Empty.getDefaultInstance()
  }

  override suspend fun add(request: CounterAddRequest): Empty {
    updateCounter(request.value)
    return Empty.getDefaultInstance()
  }

  override suspend fun get(request: CounterRequest): GetResponse {
    return getResponse { value = getCounter() }
  }

  override suspend fun getAndAdd(request: CounterAddRequest): CounterUpdateResult {
    LOG.info("Invoked get and add with " + request.value)
    val (old, new) = updateCounter(request.value)
    return counterUpdateResult {
      oldValue = old
      newValue = new
    }
  }

  private suspend fun getCounter(): Long {
    return restateContext().get(TOTAL) ?: 0L
  }

  private suspend fun updateCounter(add: Long): Pair<Long, Long> {
    val currentValue = getCounter()
    val newValue = currentValue + add

    restateContext().set(TOTAL, newValue)

    return currentValue to newValue
  }
}

fun main() {
  val vertx = Vertx.vertx()

  RestateHttpServerBuilder.builder(vertx)
      .withService(CounterKt(coroutineContext = vertx.dispatcher()))
      .buildAndListen()
}
