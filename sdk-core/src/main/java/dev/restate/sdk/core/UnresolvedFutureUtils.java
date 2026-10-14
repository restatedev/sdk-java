// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.endpoint.definition.HandlerContext.UnresolvedFuture;

/** Accessors to walk the future tree passed to {@link StateMachine#doAwait(UnresolvedFuture)}. */
public final class UnresolvedFutureUtils {

  private UnresolvedFutureUtils() {}

  /** State machine node type. */
  public enum NodeType {
    SINGLE,
    FIRST_COMPLETED,
    ALL_SUCCEEDED_OR_FIRST_FAILED,
    UNKNOWN
  }

  public static NodeType nodeType(UnresolvedFuture node) {
    if (node instanceof UnresolvedFuture.Single) {
      return NodeType.SINGLE;
    } else if (node instanceof UnresolvedFuture.AnyOf) {
      return NodeType.FIRST_COMPLETED;
    } else if (node instanceof UnresolvedFuture.AllOf) {
      return NodeType.ALL_SUCCEEDED_OR_FIRST_FAILED;
    } else if (node instanceof UnresolvedFuture.Mapped) {
      return NodeType.UNKNOWN;
    }
    throw unknownNode(node);
  }

  /**
   * @return the state machine handle of the given {@link UnresolvedFuture.Single}.
   */
  public static int handle(UnresolvedFuture single) {
    return ((NotificationHandle<?>) ((UnresolvedFuture.Single) single).handle()).value();
  }

  public static int childrenCount(UnresolvedFuture node) {
    if (node instanceof UnresolvedFuture.Combinator combinator) {
      return combinator.childrenCount();
    } else if (node instanceof UnresolvedFuture.Mapped) {
      return 1;
    } else if (node instanceof UnresolvedFuture.Single) {
      return 0;
    }
    throw unknownNode(node);
  }

  public static UnresolvedFuture child(UnresolvedFuture node, int index) {
    if (node instanceof UnresolvedFuture.Combinator combinator) {
      return combinator.child(index);
    } else if (node instanceof UnresolvedFuture.Mapped mapped && index == 0) {
      return mapped.upstream();
    }
    throw new IndexOutOfBoundsException(index);
  }

  private static IllegalArgumentException unknownNode(UnresolvedFuture node) {
    return new IllegalArgumentException("Unknown future tree node " + node.getClass());
  }
}
