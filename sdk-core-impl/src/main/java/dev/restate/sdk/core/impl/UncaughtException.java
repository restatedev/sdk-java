package dev.restate.sdk.core.impl;

/**
 * Just a marker exception used to mark an exception as uncaught in {@link
 * GrpcServerCallListenerAdaptor}.
 */
class UncaughtException extends RuntimeException {

  UncaughtException(Throwable t) {
    super(t);
  }
}
