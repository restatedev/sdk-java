// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.client.Client;
import dev.restate.sdk.testing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = Greeter.class,
    properties = {"greetingPrefix=Something something "})
@RestateTest(containerImage = "ghcr.io/restatedev/restate:main")
public class SdkTestingIntegrationTest {

  @Autowired @BindService private Greeter greeter;

  @Test
  @Timeout(value = 10)
  void greet(@RestateClient Client ingressClient) {
    var client = greeterClient.fromClient(ingressClient);

    assertThat(client.greet("Francesco")).isEqualTo("Something something Francesco");
  }
}
