package dev.restate.sdk.core.impl;

/**
 * Resolved handler for an invocation.
 *
 * <p>You MUST first wire up the processor returned by {@link #processor()} and then {@link
 * #start()} the invocation.
 */
public interface InvocationHandler {

  InvocationFlow.InvocationProcessor processor();

  void start();
}
