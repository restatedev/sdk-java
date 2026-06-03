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
import dev.restate.sdk.endpoint.Endpoint
import dev.restate.sdk.springboot.RestateEndpointConfiguration
import dev.restate.sdk.testing.RestateRunner
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistry
import io.micrometer.observation.tck.TestObservationRegistryAssert
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Verifies that [RestateEndpointConfiguration] picks up a Spring-managed [ObservationRegistry] bean
 * and wires the Kotlin Micrometer interceptor into the `Endpoint`, so a real coroutine handler
 * invocation records a `restate.invocation` observation.
 */
@SpringBootTest(
    classes =
        [
            Greeter::class,
            RestateEndpointConfiguration::class,
            MicrometerObservationTest.ObservationConfig::class,
        ],
    properties = ["greetingPrefix=Hello "],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MicrometerObservationTest {

  @Autowired lateinit var endpoint: Endpoint
  @Autowired lateinit var observationRegistry: ObservationRegistry

  private lateinit var runner: RestateRunner

  @BeforeAll
  fun startRestate() {
    runner =
        RestateRunner.from(endpoint)
            .withRestateContainerImage("ghcr.io/restatedev/restate:main")
            .build()
    runner.start()
  }

  @AfterAll
  fun stopRestate() {
    runner.stop()
  }

  @Test
  @Timeout(value = 30)
  fun recordsRestateInvocationObservation() = runTest {
    val ingressClient = Client.connect(runner.restateUrl.toString())
    val greeterClient = GreeterClient.fromClient(ingressClient)

    assertThat(greeterClient.greet("Francesco")).isEqualTo("Hello Francesco")

    val testRegistry = observationRegistry as TestObservationRegistry
    TestObservationRegistryAssert.assertThat(testRegistry)
        .hasObservationWithNameEqualTo("restate.invocation")
        .that()
        .hasBeenStarted()
        .hasBeenStopped()
        .hasContextualNameEqualTo("attempt greeter/greet")
        .hasHighCardinalityKeyValue("restate.invocation.target", "greeter/greet")
  }

  @Configuration
  open class ObservationConfig {
    @Bean open fun observationRegistry(): ObservationRegistry = TestObservationRegistry.create()
  }
}
