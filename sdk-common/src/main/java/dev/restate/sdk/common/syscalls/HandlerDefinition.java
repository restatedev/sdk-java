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

public final class HandlerDefinition {
  private final String name;
  private final Object inputSchema;
  private final Object outputSchema;
  private final InvocationHandler handler;

  public HandlerDefinition(
      String name, Object inputSchema, Object outputSchema, InvocationHandler handler) {
    this.name = name;
    this.inputSchema = inputSchema;
    this.outputSchema = outputSchema;
    this.handler = handler;
  }

  public String getName() {
    return name;
  }

  public Object getInputSchema() {
    return inputSchema;
  }

  public Object getOutputSchema() {
    return outputSchema;
  }

  public InvocationHandler getHandler() {
    return handler;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    HandlerDefinition that = (HandlerDefinition) object;
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
