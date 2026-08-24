// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.integration;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/** Result of a non-blocking producer admission attempt. */
@org.jetbrains.annotations.ApiStatus.Experimental
public sealed interface SendAttempt {

  /**
   * The invocation was accepted; the future completes on durable acknowledgement.
   *
   * @param acknowledgement future completed when Restate durably acknowledges the invocation
   */
  record Accepted(CompletableFuture<SendResult> acknowledgement) implements SendAttempt {
    public Accepted {
      Objects.requireNonNull(acknowledgement, "acknowledgement");
    }
  }

  /**
   * The invocation was not accepted because the producer was backpressured. {@code ready} completes
   * when retrying may succeed; it is a notification, not an admission reservation.
   *
   * @param ready future completed when retrying may succeed
   */
  record Backpressured(CompletableFuture<@Nullable Void> ready) implements SendAttempt {
    public Backpressured {
      Objects.requireNonNull(ready, "ready");
    }
  }
}
