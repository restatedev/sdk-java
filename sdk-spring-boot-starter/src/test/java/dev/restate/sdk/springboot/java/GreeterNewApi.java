// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.java;

import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.springboot.RestateComponent;
import org.springframework.beans.factory.annotation.Value;

@Service
@RestateComponent
@Name("greeterNewApi")
public class GreeterNewApi {

  @Value("${greetingPrefix}")
  private String greetingPrefix;

  @Handler
  public String greet(String person) {
    return greetingPrefix + person;
  }
}
