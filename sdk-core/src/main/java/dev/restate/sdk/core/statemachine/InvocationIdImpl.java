// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.statemachine;

import dev.restate.sdk.common.InvocationId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class InvocationIdImpl implements InvocationId {

  private final String id;
  private Long seed;

  InvocationIdImpl(String debugId, @Nullable Long seed) {
    this.id = debugId;
    // If random seed null, it will be computed
    this.seed = seed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InvocationIdImpl that = (InvocationIdImpl) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public long toRandomSeed() {
    if (seed == null) {
      // Hash the seed to SHA-256 to increase entropy
      MessageDigest md;
      try {
        md = MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
      byte[] digest = md.digest(id.getBytes(StandardCharsets.UTF_8));

      // Generate the long
      long n = 0;
      n |= ((long) (digest[7] & 0xFF) << (Byte.SIZE * 7));
      n |= ((long) (digest[6] & 0xFF) << (Byte.SIZE * 6));
      n |= ((long) (digest[5] & 0xFF) << (Byte.SIZE * 5));
      n |= ((long) (digest[4] & 0xFF) << (Byte.SIZE * 4));
      n |= ((long) (digest[3] & 0xFF) << (Byte.SIZE * 3));
      n |= ((digest[2] & 0xFF) << (Byte.SIZE * 2));
      n |= ((digest[1] & 0xFF) << Byte.SIZE);
      n |= (digest[0] & 0xFF);
      seed = n;
    }
    return seed;
  }

  @Override
  public String toString() {
    return id;
  }
}
