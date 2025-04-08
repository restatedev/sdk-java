// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import dev.restate.common.function.ThrowingSupplier;
import dev.restate.sdk.common.InvocationId;
import java.util.Random;
import java.util.UUID;

/**
 * Subclass of {@link Random} inherently predictable, seeded on the {@link InvocationId}, which is
 * not secret.
 *
 * <p>This instance is useful to generate identifiers, idempotency keys, and for uniform sampling
 * from a set of options. If a cryptographically secure value is needed, please generate that
 * externally using {@link Context#run(String, Class, ThrowingSupplier)}.
 *
 * <p>You <b>MUST NOT</b> use this object inside a {@link Context#run(String, Class,
 * ThrowingSupplier)}/{@link Context#runAsync(String, Class, ThrowingSupplier)}.
 */
public class RestateRandom extends Random {

  private boolean seedInitialized = false;

  RestateRandom(long randomSeed) {
    super(randomSeed);
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
    return super.next(bits);
  }
}
