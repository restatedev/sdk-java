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
import java.util.Random;
import java.util.UUID;

/**
 * Subclass of {@link Random} inherently predictable, seeded on the {@link InvocationId}, which is
 * not secret.
 *
 * <p>This instance is useful to generate identifiers, idempotency keys, and for uniform sampling
 * from a set of options. If a cryptographically secure value is needed, please generate that
 * externally using {@link RestateContext#sideEffect(Serde, ThrowingSupplier)}.
 *
 * <p>You MUST NOT use this object inside a {@link RestateContext#sideEffect(Serde,
 * ThrowingSupplier)}.
 */
public class RestateRandom extends Random {
  RestateRandom(long randomSeed) {
    super(randomSeed);
  }

  /**
   * @throws UnsupportedOperationException You cannot set the seed on RestateRandom
   */
  @Override
  public synchronized void setSeed(long seed) {
    throw new UnsupportedOperationException("You cannot set the seed on RestateRandom");
  }

  /**
   * @return a UUID generated using this RNG.
   */
  public UUID nextUUID() {
    return new UUID(this.nextLong(), this.nextLong());
  }
}
