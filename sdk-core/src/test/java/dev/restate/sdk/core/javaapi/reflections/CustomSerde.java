// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.javaapi.reflections;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.annotation.CustomSerdeFactory;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.core.javaapi.MySerdeFactory;

@Service
@CustomSerdeFactory(MySerdeFactory.class)
public class CustomSerde {
  @Handler
  public String greet(String request) {
    assertThat(request).isEqualTo("INPUT");
    return "output";
  }
}
