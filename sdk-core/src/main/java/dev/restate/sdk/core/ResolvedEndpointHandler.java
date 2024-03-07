// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

/**
 * Resolved handler for an invocation.
 *
 * <p>You MUST first wire up the subscriber and publisher returned by {@link #input()} and {@link
 * #output()} and then {@link #start()} the invocation.
 */
public interface ResolvedEndpointHandler {

  InvocationFlow.InvocationInputSubscriber input();

  InvocationFlow.InvocationOutputPublisher output();

  void start();
}
