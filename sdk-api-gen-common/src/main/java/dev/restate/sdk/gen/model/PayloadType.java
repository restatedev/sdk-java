// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen.model;

import java.util.Objects;

public class PayloadType {

  private final boolean isEmpty;
  private final String name;
  private final String boxed;
  private final String serdeDecl;

  public PayloadType(boolean isEmpty, String name, String boxed, String serdeDecl) {
    this.isEmpty = isEmpty;
    this.name = name;
    this.boxed = boxed;
    this.serdeDecl = serdeDecl;
  }

  public boolean isEmpty() {
    return isEmpty;
  }

  public String getName() {
    return name;
  }

  public String getBoxed() {
    return boxed;
  }

  public String getSerdeDecl() {
    return serdeDecl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PayloadType)) return false;
    PayloadType that = (PayloadType) o;
    return isEmpty == that.isEmpty
        && Objects.equals(name, that.name)
        && Objects.equals(boxed, that.boxed)
        && Objects.equals(serdeDecl, that.serdeDecl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, boxed, serdeDecl);
  }

  @Override
  public String toString() {
    return "PayloadType{"
        + "name='"
        + name
        + '\''
        + ", boxed='"
        + boxed
        + '\''
        + ", serdeDecl='"
        + serdeDecl
        + '\''
        + '}';
  }
}
