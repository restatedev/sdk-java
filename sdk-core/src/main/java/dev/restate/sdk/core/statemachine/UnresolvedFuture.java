// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import java.util.ArrayList;
import java.util.List;

/** Represents an unresolved future tree that the SDK is awaiting on. */
public sealed interface UnresolvedFuture
    permits UnresolvedFuture.Single,
        UnresolvedFuture.FirstCompleted,
        UnresolvedFuture.AllCompleted,
        UnresolvedFuture.FirstSucceededOrAllFailed,
        UnresolvedFuture.AllSucceededOrFirstFailed,
        UnresolvedFuture.Unknown {

  record Single(int handle) implements UnresolvedFuture {}

  record FirstCompleted(List<UnresolvedFuture> children) implements UnresolvedFuture {}

  record AllCompleted(List<UnresolvedFuture> children) implements UnresolvedFuture {}

  record FirstSucceededOrAllFailed(List<UnresolvedFuture> children) implements UnresolvedFuture {}

  record AllSucceededOrFirstFailed(List<UnresolvedFuture> children) implements UnresolvedFuture {}

  record Unknown(List<UnresolvedFuture> children) implements UnresolvedFuture {}

  /** Collect all leaf handles from this future tree. */
  default List<Integer> handles() {
    var result = new ArrayList<Integer>();
    collectHandles(this, result);
    return result;
  }

  private static void collectHandles(UnresolvedFuture fut, List<Integer> out) {
    if (fut instanceof Single s) {
      out.add(s.handle());
    } else if (fut instanceof FirstCompleted fc) {
      fc.children().forEach(c -> collectHandles(c, out));
    } else if (fut instanceof AllCompleted ac) {
      ac.children().forEach(c -> collectHandles(c, out));
    } else if (fut instanceof FirstSucceededOrAllFailed fsaf) {
      fsaf.children().forEach(c -> collectHandles(c, out));
    } else if (fut instanceof AllSucceededOrFirstFailed asff) {
      asff.children().forEach(c -> collectHandles(c, out));
    } else if (fut instanceof Unknown u) {
      u.children().forEach(c -> collectHandles(c, out));
    }
  }
}
