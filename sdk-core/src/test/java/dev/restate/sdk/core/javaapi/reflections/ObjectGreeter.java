// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import dev.restate.sdk.annotation.Exclusive;
import dev.restate.sdk.annotation.Shared;
import dev.restate.sdk.annotation.VirtualObject;

@VirtualObject
public class ObjectGreeter {
  @Exclusive
  public String greet(String request) {
    return request;
  }

  @Shared
  public String sharedGreet(String request) {
    return request;
  }
}
