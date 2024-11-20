// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.sdk.client.Client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@RestateTest(containerImage = "ghcr.io/restatedev/restate:main")
class CounterTest {

  @BindService private final Counter counter = new Counter();

  @Test
  @Timeout(value = 10)
  void testGreet(@RestateClient Client ingressClient) {
    var client = CounterClient.fromClient(ingressClient, "my-counter");

    long response = client.get();
    assertThat(response).isEqualTo(0L);
  }
}
