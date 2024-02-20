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

public final class Address {

  private final String service;
  private final String method;
  private final String key;

  private Address(String service, String method, String key) {
    this.service = service;
    this.method = method;
    this.key = key;
  }

  public static Address forObject(String service, String method, String key) {
    return new Address(service, method, key);
  }

  public static Address forStateless(String service, String method) {
    return new Address(service, method, null);
  }

  public static Address forWorkflow(String service, String method, String key) {
    return new Address(service, method, key);
  }

  public String getService() {
    return service;
  }

  public String getMethod() {
    return method;
  }

  public String getKey() {
    return key;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    Address address = (Address) object;
    return Objects.equals(service, address.service)
        && Objects.equals(method, address.method)
        && Objects.equals(key, address.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(service, method, key);
  }

  @Override
  public String toString() {
    if (key == null) {
      return service + "/" + method;
    }
    return service + "/" + key + "/" + method;
  }
}
