// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class NameInferenceTest {

  @Test
  void expectedName() {
    assertThat(CodegenTestServiceGreeterDefinitions.SERVICE_NAME)
        .isEqualTo("CodegenTestServiceGreeter");
    assertThat(GreeterWithoutExplicitNameDefinitions.SERVICE_NAME)
        .isEqualTo("GreeterWithoutExplicitName");
    assertThat(MyExplicitNameDefinitions.SERVICE_NAME).isEqualTo("MyExplicitName");
  }
}
