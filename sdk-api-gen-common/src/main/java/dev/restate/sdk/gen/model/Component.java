// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import dev.restate.sdk.common.ComponentType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class Component {

  private final CharSequence targetPkg;
  private final CharSequence targetFqcn;
  private final String componentName;
  private final ComponentType componentType;
  private final List<Handler> handlers;

  public Component(
      CharSequence targetPkg,
      CharSequence targetFqcn,
      String componentName,
      ComponentType componentType,
      List<Handler> handlers) {
    this.targetPkg = targetPkg;
    this.targetFqcn = targetFqcn;
    this.componentName = componentName;

    this.componentType = componentType;
    this.handlers = handlers;
  }

  public CharSequence getTargetPkg() {
    return this.targetPkg;
  }

  public CharSequence getTargetFqcn() {
    return this.targetFqcn;
  }

  public String getFullyQualifiedComponentName() {
    return this.componentName;
  }

  public String getSimpleComponentName() {
    return this.componentName.substring(this.componentName.lastIndexOf('.') + 1);
  }

  public CharSequence getGeneratedClassFqcnPrefix() {
    if (this.targetPkg == null || this.targetPkg.length() == 0) {
      return getSimpleComponentName();
    }
    return this.targetPkg + "." + getSimpleComponentName();
  }

  public ComponentType getComponentType() {
    return componentType;
  }

  public List<Handler> getMethods() {
    return handlers;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private CharSequence targetPkg;
    private CharSequence targetFqcn;
    private String componentName;
    private ComponentType componentType;
    private final List<Handler> handlers = new ArrayList<>();

    public Builder withTargetPkg(CharSequence targetPkg) {
      this.targetPkg = targetPkg;
      return this;
    }

    public Builder withTargetFqcn(CharSequence targetFqcn) {
      this.targetFqcn = targetFqcn;
      return this;
    }

    public Builder withComponentName(String componentName) {
      this.componentName = componentName;
      return this;
    }

    public Builder withComponentType(ComponentType componentType) {
      this.componentType = componentType;
      return this;
    }

    public Builder withHandlers(Collection<Handler> handlers) {
      this.handlers.addAll(handlers);
      return this;
    }

    public Builder withHandler(Handler handler) {
      this.handlers.add(handler);
      return this;
    }

    public CharSequence getTargetPkg() {
      return targetPkg;
    }

    public CharSequence getTargetFqcn() {
      return targetFqcn;
    }

    public String getComponentName() {
      return componentName;
    }

    public ComponentType getComponentType() {
      return componentType;
    }

    public List<Handler> getHandlers() {
      return handlers;
    }

    public Component build() {
      return new Component(
          Objects.requireNonNull(targetPkg),
          Objects.requireNonNull(targetFqcn),
          Objects.requireNonNull(componentName),
          Objects.requireNonNull(componentType),
          handlers);
    }
  }
}
