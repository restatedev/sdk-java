package dev.restate.sdk.blocking;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.syscalls.Syscalls;

/**
 * Marker interface for Restate blocking services.
 *
 * <h2>Error handling</h2>
 *
 * The error handling of Restate services works as follows:
 *
 * <ul>
 *   <li>When throwing {@link io.grpc.StatusException} or {@link io.grpc.StatusRuntimeException},
 *       the failure is considered "terminal" and will be used as invocation output
 *   <li>When throwing any other type of exception, the failure is considered "non-terminal" and the
 *       runtime will retry it, according to its configuration
 *   <li>In case {@code StreamObserver#onError} is invoked, the failure is considered "terminal"
 * </ul>
 */
public interface RestateBlockingService extends BindableBlockingService {

  /**
   * @return an instance of the {@link RestateContext}.
   */
  default RestateContext restateContext() {
    return RestateContext.fromSyscalls(Syscalls.current());
  }
}
