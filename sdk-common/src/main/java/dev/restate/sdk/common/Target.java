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

  private final String service;
  private final String handler;
  private final String key;

  private Target(String service, String handler, String key) {
    this.service = service;
    this.handler = handler;
    this.key = key;
  }

  public static Target virtualObject(String name, String key, String handler) {
    return new Target(name, handler, key);
  }

  public static Target service(String name, String handler) {
    return new Target(name, handler, null);
  }

  public String getService() {
    return service;
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
    return Objects.equals(service, target.service)
        && Objects.equals(handler, target.handler)
        && Objects.equals(key, target.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(service, handler, key);
  }

  @Override
  public String toString() {
    if (key == null) {
      return service + "/" + handler;
    }
    return service + "/" + key + "/" + handler;
  }
}
