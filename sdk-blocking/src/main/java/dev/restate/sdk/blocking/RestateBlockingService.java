package dev.restate.sdk.blocking;

import dev.restate.sdk.core.BindableBlockingService;
import dev.restate.sdk.core.syscalls.Syscalls;

public interface RestateBlockingService extends BindableBlockingService {

  default RestateContext restateContext() {
    return new RestateContextImpl(Syscalls.current());
  }
}
