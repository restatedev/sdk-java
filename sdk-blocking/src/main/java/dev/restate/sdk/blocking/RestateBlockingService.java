package dev.restate.sdk.blocking;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.syscalls.Syscalls;

/** Marker interface for Restate blocking services. */
public interface RestateBlockingService extends BindableBlockingService {

  /**
   * @return an instance of the {@link RestateContext}.
   */
  default RestateContext restateContext() {
    return new RestateContextImpl(Syscalls.current());
  }
}
