package dev.restate.sdk.core.impl;

import com.google.protobuf.ByteString;
import dev.restate.sdk.core.InvocationId;
import java.util.Base64;
import java.util.Objects;

final class InvocationIdImpl implements InvocationId {

  private final String id;

  InvocationIdImpl(String serviceName, ByteString instanceKey, ByteString invocationId) {
    this.id =
        serviceName
            + "-"
            + Base64.getEncoder().encodeToString(instanceKey.toByteArray())
            + "-"
            + Base64.getEncoder().encodeToString(invocationId.toByteArray());
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
