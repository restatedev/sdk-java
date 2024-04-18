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
  private final Object inputSchema;
  private final Object outputSchema;
  private final InvocationHandler<O> handler;

  public HandlerDefinition(
      String name,
      HandlerType handlerType,
      Object inputSchema,
      Object outputSchema,
      InvocationHandler<O> handler) {
    this.name = name;
    this.handlerType = handlerType;
    this.inputSchema = inputSchema;
    this.outputSchema = outputSchema;
    this.handler = handler;
  }

  public String getName() {
    return name;
  }

  public HandlerType getHandlerType() {
    return handlerType;
  }

  public Object getInputSchema() {
    return inputSchema;
  }

  public Object getOutputSchema() {
    return outputSchema;
  }

  public InvocationHandler<O> getHandler() {
    return handler;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    HandlerDefinition<?> that = (HandlerDefinition<?>) object;
    return Objects.equals(name, that.name)
        && Objects.equals(inputSchema, that.inputSchema)
        && Objects.equals(outputSchema, that.outputSchema)
        && Objects.equals(handler, that.handler);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, inputSchema, outputSchema, handler);
  }
}
