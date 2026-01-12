// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import dev.restate.sdk.Restate;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.annotation.Service;

@Service
@Name("Empty")
public class Empty {

  @Handler
  public String emptyInput() {
    return Restate.service(Empty.class).emptyInput();
  }

  @Handler
  public void emptyOutput(String request) {
    Restate.service(Empty.class).emptyOutput(request);
  }

  @Handler
  public void emptyInputOutput() {
    Restate.service(Empty.class).emptyInputOutput();
  }
}
