package dev.restate.sdk.core;

public final class SuspendedException extends Throwable {
  @SuppressWarnings("StaticAssignmentOfThrowable")
  public static final SuspendedException INSTANCE = new SuspendedException();

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow() throws E {
    throw (E) SuspendedException.INSTANCE;
  }

  private SuspendedException() {
    super("SuspendedException");
    setStackTrace(new StackTraceElement[] {});
  }
}
