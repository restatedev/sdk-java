// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

public final class SendHandle {

  private final Awaitable<String> invocationIdAwaitable;

  SendHandle(Awaitable<String> invocationIdAwaitable) {
    this.invocationIdAwaitable = invocationIdAwaitable;
  }

  public String invocationId() {
    return this.invocationIdAwaitable.await();
  }
}
