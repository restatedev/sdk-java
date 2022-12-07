import dev.restate.sdk.core.BindableNonBlockingService
import dev.restate.sdk.core.syscalls.Syscalls
import dev.restate.sdk.kotlin.RestateContext
import dev.restate.sdk.kotlin.RestateContextImpl

interface RestateCoroutineService : BindableNonBlockingService {
  fun restateContext(): RestateContext {
    return RestateContextImpl(Syscalls.SYSCALLS_KEY.get())
  }
}
