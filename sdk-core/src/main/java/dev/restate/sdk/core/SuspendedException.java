package dev.restate.sdk.core;

public final class SuspendedException extends RuntimeException {
  @SuppressWarnings("StaticAssignmentOfThrowable")
  public static final SuspendedException INSTANCE = new SuspendedException();

  private SuspendedException() {
    super("SuspendedException");
    setStackTrace(new StackTraceElement[] {});
  }
}
