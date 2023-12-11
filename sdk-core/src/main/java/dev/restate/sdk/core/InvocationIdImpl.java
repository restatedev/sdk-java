// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core;

import dev.restate.sdk.common.InvocationId;
import java.util.Objects;

final class InvocationIdImpl implements InvocationId {

  private final String id;

  InvocationIdImpl(String debugId) {
    this.id = debugId;
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
    return stringToSeed(id);
  }

  @Override
  public String toString() {
    return id;
  }

  // Thanks https://stackoverflow.com/questions/12458383/java-random-numbers-using-a-seed
  static long stringToSeed(String s) {
    if (s == null) {
      return 0;
    }
    long hash = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      hash = 31L * hash + c;
    }
    return hash;
  }
}
