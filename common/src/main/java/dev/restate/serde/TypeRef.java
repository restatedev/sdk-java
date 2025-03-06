// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.serde;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * This generic abstract class is used for obtaining full generics type information by sub-classing.
 * Similar to Jackson's TypeReference.
 */
public abstract class TypeRef<T> implements TypeTag<T> {
  private final Type type;

  protected TypeRef() {
    Type superClass = this.getClass().getGenericSuperclass();
    if (superClass instanceof java.lang.Class<?>) {
      throw new IllegalArgumentException(
          "Internal error: TypeRef constructed without actual type information");
    } else {
      this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }
  }

  public Type getType() {
    return this.type;
  }
}
