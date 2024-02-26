// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.common;

import java.util.Objects;

public final class Target {

  private final String component;
  private final String handler;
  private final String key;

  private Target(String component, String handler, String key) {
    this.component = component;
    this.handler = handler;
    this.key = key;
  }

  public static Target virtualObject(String name, String key, String handler) {
    return new Target(name, handler, key);
  }

  public static Target service(String name, String handler) {
    return new Target(name, handler, null);
  }

  public String getComponent() {
    return component;
  }

  public String getHandler() {
    return handler;
  }

  public String getKey() {
    return key;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    Target target = (Target) object;
    return Objects.equals(component, target.component)
        && Objects.equals(handler, target.handler)
        && Objects.equals(key, target.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(component, handler, key);
  }

  @Override
  public String toString() {
    if (key == null) {
      return component + "/" + handler;
    }
    return component + "/" + key + "/" + handler;
  }
}
