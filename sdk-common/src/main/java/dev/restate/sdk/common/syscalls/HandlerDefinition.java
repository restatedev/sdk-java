// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.HandlerType;
import java.util.Objects;

public final class HandlerDefinition<O> {
  private final String name;
  private final HandlerType handlerType;
  private final InvocationHandler<O> handler;

  public HandlerDefinition(String name, HandlerType handlerType, InvocationHandler<O> handler) {
    this.name = name;
    this.handlerType = handlerType;
    this.handler = handler;
  }

  public String getName() {
    return name;
  }

  public HandlerType getHandlerType() {
    return handlerType;
  }

  public InvocationHandler<O> getHandler() {
    return handler;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    HandlerDefinition<?> that = (HandlerDefinition<?>) object;
    return Objects.equals(name, that.name) && Objects.equals(handler, that.handler);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, handler);
  }
}
