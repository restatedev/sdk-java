package dev.restate.sdk.core.impl;

import dev.restate.sdk.core.InvocationId;
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
  public String toString() {
    return id;
  }
}
