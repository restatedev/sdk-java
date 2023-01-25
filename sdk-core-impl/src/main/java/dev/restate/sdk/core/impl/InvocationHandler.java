package dev.restate.sdk.core.impl;

/**
 * Resolved handler for an invocation.
 *
 * <p>You MUST first wire up the subscriber and publisher returned by {@link #input()} and {@link
 * #output()} and then {@link #start()} the invocation.
 */
public interface InvocationHandler {

  InvocationFlow.InvocationInputSubscriber input();

  InvocationFlow.InvocationOutputPublisher output();

  void start();
}
