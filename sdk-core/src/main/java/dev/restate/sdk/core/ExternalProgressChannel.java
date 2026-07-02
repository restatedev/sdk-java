// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import org.jspecify.annotations.Nullable;

final class ExternalProgressChannel {

  private int pending = 0;
  private @Nullable Runnable waiter;

  void signal() {
    if (waiter != null) {
      Runnable w = waiter;
      waiter = null;
      w.run();
    } else {
      pending++;
    }
  }

  void awaitNext(Runnable callback) {
    if (waiter != null) {
      throw new IllegalStateException("awaitNext already pending");
    }
    if (pending > 0) {
      pending--;
      callback.run();
      return;
    }
    waiter = callback;
  }
}
