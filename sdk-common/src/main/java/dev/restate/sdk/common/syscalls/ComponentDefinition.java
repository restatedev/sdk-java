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

public final class ComponentDefinition {

  private final String fullyQualifiedServiceName;
  private final ExecutorType executorType;
  private final ComponentType componentType;
  private final Map<String, HandlerDefinition> handlers;

  public ComponentDefinition(
      String fullyQualifiedServiceName,
      ExecutorType executorType,
      ComponentType componentType,
      Collection<HandlerDefinition> handlers) {
    this.fullyQualifiedServiceName = fullyQualifiedServiceName;
    this.executorType = executorType;
    this.componentType = componentType;
    this.handlers =
        handlers.stream()
            .collect(Collectors.toMap(HandlerDefinition::getName, Function.identity()));
  }

  public String getFullyQualifiedServiceName() {
    return fullyQualifiedServiceName;
  }

  public ExecutorType getExecutorType() {
    return executorType;
  }

  public ComponentType getComponentType() {
    return componentType;
  }

  public Collection<HandlerDefinition> getHandlers() {
    return handlers.values();
  }

  public HandlerDefinition getHandler(String name) {
    return handlers.get(name);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    ComponentDefinition that = (ComponentDefinition) object;
    return Objects.equals(fullyQualifiedServiceName, that.fullyQualifiedServiceName)
        && executorType == that.executorType
        && componentType == that.componentType
        && Objects.equals(handlers, that.handlers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fullyQualifiedServiceName, executorType, componentType, handlers);
  }
}
