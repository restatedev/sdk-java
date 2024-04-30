// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import java.util.Objects;

public final class HandlerDefinition<REQ, RES, O> {

  private final HandlerSpecification<REQ, RES> spec;
  private final InvocationHandler<REQ, RES, O> handler;

  HandlerDefinition(
          HandlerSpecification<REQ, RES> spec,
          InvocationHandler<REQ, RES, O> handler) {
      this.spec = spec;
      this.handler = handler;
  }

  public HandlerSpecification<REQ, RES> getSpec() {
    return spec;
  }

  public InvocationHandler<REQ, RES, O> getHandler() {
    return handler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerDefinition<?, ?, ?> that = (HandlerDefinition<?, ?, ?>) o;
    return Objects.equals(spec, that.spec) && Objects.equals(handler, that.handler);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(spec);
    result = 31 * result + Objects.hashCode(handler);
    return result;
  }

  @Override
  public String toString() {
    return "HandlerDefinition{" +
            "spec=" + spec +
            ", handler=" + handler +
            '}';
  }

  public static <REQ, RES, O> HandlerDefinition<REQ, RES, O> of( HandlerSpecification<REQ, RES> spec,
                                                                 InvocationHandler<REQ, RES, O> handler) {
    return new HandlerDefinition<>(spec, handler);
  }
}
