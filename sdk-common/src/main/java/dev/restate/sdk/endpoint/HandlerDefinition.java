// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.endpoint;

import java.util.Objects;

public final class HandlerDefinition<REQ, RES, O> {

  private final HandlerSpecification<REQ, RES> spec;
  private final HandlerRunner<REQ, RES, O> runner;

  HandlerDefinition(HandlerSpecification<REQ, RES> spec, HandlerRunner<REQ, RES, O> runner) {
    this.spec = spec;
    this.runner = runner;
  }

  public HandlerSpecification<REQ, RES> getSpec() {
    return spec;
  }

  public HandlerRunner<REQ, RES, O> getRunner() {
    return runner;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HandlerDefinition<?, ?, ?> that = (HandlerDefinition<?, ?, ?>) o;
    return Objects.equals(spec, that.spec) && Objects.equals(runner, that.runner);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(spec);
    result = 31 * result + Objects.hashCode(runner);
    return result;
  }

  @Override
  public String toString() {
    return "HandlerDefinition{" + "spec=" + spec + ", handler=" + runner + '}';
  }

  public static <REQ, RES, O> HandlerDefinition<REQ, RES, O> of(
      HandlerSpecification<REQ, RES> spec, HandlerRunner<REQ, RES, O> runner) {
    return new HandlerDefinition<>(spec, runner);
  }
}
