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

import dev.restate.sdk.examples.generated.CounterGrpc;
import dev.restate.sdk.examples.generated.CounterRequest;
import dev.restate.sdk.examples.generated.GetResponse;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CounterTest {

  @RegisterExtension
  private static final RestateRunner restateRunner =
      RestateRunnerBuilder.create()
          .withRestateContainerImage(
              "ghcr.io/restatedev/restate:main") // test against the latest main Restate image
          .withService(new Counter())
          .buildRunner();

  @Test
  void testGreet(@RestateGrpcChannel ManagedChannel channel) {
    CounterGrpc.CounterBlockingStub client = CounterGrpc.newBlockingStub(channel);
    GetResponse response = client.get(CounterRequest.getDefaultInstance());

    assertThat(response.getValue()).isEqualTo(0);
  }
}
