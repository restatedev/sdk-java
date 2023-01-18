package dev.restate.sdk.core.syscalls;

import io.grpc.StatusRuntimeException;
import javax.annotation.Nullable;

public interface ExitSideEffectSyscallCallback<T> {

  void onResult(@Nullable T t);

  /** This is user failure. */
  void onFailure(StatusRuntimeException t);

  /**
   * This is internal failure propagation, causing a cancellation of the processing. For example
   * this can be {@link dev.restate.sdk.core.SuspendedException} when the network layer is
   * suspending the function.
   */
  void onCancel(@Nullable Throwable t);
}