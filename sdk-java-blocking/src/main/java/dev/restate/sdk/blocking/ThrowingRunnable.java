package dev.restate.sdk.blocking;

/** Like {@link Runnable} but can throw checked exceptions. */
@FunctionalInterface
public interface ThrowingRunnable {

  /** Run, potentially throwing an exception. */
  void run() throws Throwable;
}
