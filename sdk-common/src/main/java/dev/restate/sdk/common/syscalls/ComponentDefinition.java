// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common.syscalls;

import dev.restate.sdk.common.ComponentType;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ComponentDefinition<O> {

  private final String fullyQualifiedComponentName;
  private final ComponentType componentType;
  private final Map<String, HandlerDefinition<O>> handlers;

  public ComponentDefinition(
      String fullyQualifiedComponentName,
      ComponentType componentType,
      Collection<HandlerDefinition<O>> handlers) {
    this.fullyQualifiedComponentName = fullyQualifiedComponentName;
    this.componentType = componentType;
    this.handlers =
        handlers.stream()
            .collect(Collectors.toMap(HandlerDefinition::getName, Function.identity()));
  }

  public String getFullyQualifiedComponentName() {
    return fullyQualifiedComponentName;
  }

  public ComponentType getComponentType() {
    return componentType;
  }

  public Collection<HandlerDefinition<O>> getHandlers() {
    return handlers.values();
  }

  public HandlerDefinition<O> getHandler(String name) {
    return handlers.get(name);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    ComponentDefinition<?> that = (ComponentDefinition<?>) object;
    return Objects.equals(fullyQualifiedComponentName, that.fullyQualifiedComponentName)
        && componentType == that.componentType
        && Objects.equals(handlers, that.handlers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fullyQualifiedComponentName, componentType, handlers);
  }
}
