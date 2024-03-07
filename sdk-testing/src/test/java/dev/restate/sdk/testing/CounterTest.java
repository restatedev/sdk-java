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

import dev.restate.sdk.client.IngressClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CounterTest {

  @RegisterExtension
  private static final RestateRunner RESTATE_RUNNER =
      RestateRunnerBuilder.create()
          .withRestateContainerImage(
              "ghcr.io/restatedev/restate:main") // test against the latest main Restate image
          .with(new Counter())
          .buildRunner();

  @Test
  void testGreet(@RestateIngressClient IngressClient ingressClient) {
    var client = CounterClient.fromIngress(ingressClient, "my-counter");
    long response = client.get();

    assertThat(response).isEqualTo(0L);
  }
}
