// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.sdk.common.InvocationId;
import dev.restate.sdk.common.Serde;
import dev.restate.sdk.common.function.ThrowingSupplier;
import dev.restate.sdk.common.syscalls.Syscalls;
import java.util.Random;
import java.util.UUID;

/**
 * Subclass of {@link Random} inherently predictable, seeded on the {@link InvocationId}, which is
 * not secret.
 *
 * <p>This instance is useful to generate identifiers, idempotency keys, and for uniform sampling
 * from a set of options. If a cryptographically secure value is needed, please generate that
 * externally using {@link ObjectContext#run(Serde, ThrowingSupplier)}.
 *
 * <p>You MUST NOT use this object inside a {@link ObjectContext#run(Serde, ThrowingSupplier)}.
 */
public class RestateRandom extends Random {

  private final Syscalls syscalls;
  private boolean seedInitialized = false;

  RestateRandom(long randomSeed, Syscalls syscalls) {
    super(randomSeed);
    this.syscalls = syscalls;
  }

  /**
   * @throws UnsupportedOperationException You cannot set the seed on RestateRandom
   */
  @Override
  public synchronized void setSeed(long seed) {
    if (seedInitialized) {
      throw new UnsupportedOperationException("You cannot set the seed on RestateRandom");
    }
    super.setSeed(seed);
    this.seedInitialized = true;
  }

  /**
   * @return a UUID generated using this RNG that is stable across retries and replays.
   */
  public UUID nextUUID() {
    return new UUID(this.nextLong(), this.nextLong());
  }

  @Override
  protected int next(int bits) {
    if (this.syscalls.isInsideSideEffect()) {
      throw new IllegalStateException("You can't use RestateRandom inside ctx.run!");
    }

    return super.next(bits);
  }
}
