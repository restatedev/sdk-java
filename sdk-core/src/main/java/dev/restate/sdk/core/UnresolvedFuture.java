// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import java.util.List;

public sealed interface UnresolvedFuture permits AsyncResults.AsyncResultInternal {
  boolean isDone();

  /** This await node's combinator kind; {@link Kind#SINGLE} for a leaf. */
  Kind kind();

  /** The notification handle; valid only when {@link #kind()} is {@link Kind#SINGLE}. */
  int singleHandle();

  /** Child await nodes; empty for a {@link Kind#SINGLE} leaf. */
  List<? extends UnresolvedFuture> combinatorChildren();

  /** Await-tree node kind, walked by the state machine to encode the {@code do_await} request. */
  enum Kind {
    SINGLE,
    FIRST_COMPLETED,
    ALL_COMPLETED,
    FIRST_SUCCEEDED_OR_ALL_FAILED,
    ALL_SUCCEEDED_OR_FIRST_FAILED,
    UNKNOWN
  }
}
