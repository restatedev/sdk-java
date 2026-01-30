// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.kotlin

import dev.restate.client.Client
import dev.restate.client.kotlin.service
import dev.restate.client.kotlin.toService
import dev.restate.sdk.testing.BindService
import dev.restate.sdk.testing.RestateClient
import dev.restate.sdk.testing.RestateTest
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    classes = [Greeter::class, GreeterNewApi::class],
    properties = ["greetingPrefix=Something something "],
)
@RestateTest(containerImage = "ghcr.io/restatedev/restate:main")
class SdkTestingIntegrationTest {
  @Autowired @BindService lateinit var greeter: Greeter
  @Autowired @BindService lateinit var greeterNewApi: GreeterNewApi

  @Test
  @Timeout(value = 10)
  fun greet(@RestateClient ingressClient: Client) = runTest {
    val client = GreeterClient.fromClient(ingressClient)

    assertThat(client.greet("Francesco")).isEqualTo("Something something Francesco")
  }

  @Test
  @Timeout(value = 10)
  fun greetNewApi(@RestateClient ingressClient: Client) = runTest {
    assertThat(ingressClient.service<GreeterNewApi>().greet("Francesco"))
        .isEqualTo("Something something Francesco")
  }

  @Test
  @Timeout(value = 10)
  fun greetNewApiWithRequestTo(@RestateClient ingressClient: Client) = runTest {
    val response: String =
        ingressClient.toService<GreeterNewApi>().request { greet("Francesco") }.call().response()

    assertThat(response).isEqualTo("Something something Francesco")
  }
}
