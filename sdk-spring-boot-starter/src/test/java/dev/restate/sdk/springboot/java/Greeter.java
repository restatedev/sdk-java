// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.java;

import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Name;
import dev.restate.sdk.springboot.RestateService;
import org.springframework.beans.factory.annotation.Value;

@RestateService(configuration = "greeterConfiguration")
@Name("greeter")
public class Greeter {

  @Value("${greetingPrefix}")
  private String greetingPrefix;

  @Handler
  public String greet(Context ctx, String person) {
    return greetingPrefix + person;
  }
}
