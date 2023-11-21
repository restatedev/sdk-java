package dev.restate.sdk.core;

/** You MUST NOT catch this exception. */
public final class AbortedExecutionException extends Throwable {
  @SuppressWarnings("StaticAssignmentOfThrowable")
  public static final AbortedExecutionException INSTANCE = new AbortedExecutionException();

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow() throws E {
    throw (E) AbortedExecutionException.INSTANCE;
  }

  private AbortedExecutionException() {
    super("AbortedExecutionException");
    setStackTrace(new StackTraceElement[] {});
  }
}
