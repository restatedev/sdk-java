package dev.restate.sdk.blocking;

import dev.restate.sdk.core.BindableBlockingService;

public interface RestateBlockingService extends BindableBlockingService {

  default RestateContext restateContext() {
    return RestateContext.current();
  }
}
