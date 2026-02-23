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
import dev.restate.sdk.annotation.Service;

@Service
public class ServiceWithInterface {

  public interface SharedInterface {
    @Handler
    String greet(String name);
  }

  @Handler
  public String callInterface(String name) {
    return Restate.service(SharedInterface.class, "MyGreeter").greet(name);
  }

  @Handler
  public String callInterfaceHandle(String name) {
    return Restate.serviceHandle(SharedInterface.class, "MyGreeterHandle")
        .call(SharedInterface::greet, name)
        .await();
  }
}
