// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Represents an invocation target. */
public final class Target {

  private final @Nullable String scope;
  private final String service;
  private final String handler;
  private final @Nullable String key;

  private Target(@Nullable String scope, String service, String handler, @Nullable String key) {
    this.scope = scope;
    this.service = service;
    this.handler = handler;
    this.key = key;
  }

  public static Target virtualObject(String name, String key, String handler) {
    return new Target(null, name, handler, key);
  }

  public static Target virtualObject(String scope, String name, String key, String handler) {
    return new Target(scope, name, handler, key);
  }

  public static Target workflow(String name, String key, String handler) {
    return new Target(null, name, handler, key);
  }

  public static Target workflow(String scope, String name, String key, String handler) {
    return new Target(scope, name, handler, key);
  }

  public static Target service(String name, String handler) {
    return new Target(null, name, handler, null);
  }

  public static Target service(String scope, String name, String handler) {
    return new Target(scope, name, handler, null);
  }

  /** Returns a new Target with the given scope. Requires Restate >= 1.7. */
  public Target scoped(String scope) {
    return new Target(scope, this.service, this.handler, this.key);
  }

  /**
   * @return the scope. Null if no scope is set.
   */
  public @Nullable String getScope() {
    return scope;
  }

  public String getService() {
    return service;
  }

  public String getHandler() {
    return handler;
  }

  /**
   * @return the virtual object/workflow key. Null if the target is a regular service.
   */
  public @Nullable String getKey() {
    return key;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    Target target = (Target) object;
    return Objects.equals(scope, target.scope)
        && Objects.equals(service, target.service)
        && Objects.equals(handler, target.handler)
        && Objects.equals(key, target.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scope, service, handler, key);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (scope != null) {
      sb.append(scope).append("/");
    }
    sb.append(service).append("/");
    if (key != null) {
      sb.append(key).append("/");
    }
    sb.append(handler);
    return sb.toString();
  }
}
