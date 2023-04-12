package dev.restate.sdk.core.syscalls;

public interface AnyDeferredResult extends DeferredResult<Object> {

  /**
   * @return -1 if not completed, otherwise the index in the order of the deferred provided in any
   *     constructor.
   */
  int completedIndex();
}
