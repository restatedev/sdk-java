// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.springboot.java;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import dev.restate.client.Client;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.springboot.RestateEndpointConfiguration;
import dev.restate.sdk.testing.RestateRunner;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that {@link RestateEndpointConfiguration} picks up a Spring-managed {@link
 * ObservationRegistry} bean and wires the Micrometer interceptor into the {@code Endpoint}, so a
 * real handler invocation records a {@code restate.invocation} observation.
 */
@SpringBootTest(
    classes = {
      Greeter.class,
      ServicesConfiguration.class,
      RestateEndpointConfiguration.class,
      MicrometerObservationTest.ObservationConfig.class
    },
    properties = {"greetingPrefix=Hello "})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MicrometerObservationTest {

  @Autowired private Endpoint endpoint;
  @Autowired private ObservationRegistry observationRegistry;

  private RestateRunner runner;

  @BeforeAll
  void startRestate() {
    runner =
        RestateRunner.from(endpoint)
            .withRestateContainerImage("ghcr.io/restatedev/restate:main")
            .build();
    runner.start();
  }

  @AfterAll
  void stopRestate() {
    if (runner != null) {
      runner.stop();
    }
  }

  @Test
  @Timeout(value = 30)
  void recordsRestateInvocationObservation() {
    Client ingressClient = Client.connect(runner.getRestateUrl().toString());
    var greeterClient = GreeterClient.fromClient(ingressClient);

    assertThat(greeterClient.greet("Francesco")).isEqualTo("Hello Francesco");

    var testRegistry = (TestObservationRegistry) observationRegistry;
    assertThat(testRegistry)
        .hasObservationWithNameEqualTo("restate.invocation")
        .that()
        .hasBeenStarted()
        .hasBeenStopped()
        .hasLowCardinalityKeyValue("restate.service", "greeter")
        .hasLowCardinalityKeyValue("restate.handler", "greet");
  }

  @Configuration
  static class ObservationConfig {
    @Bean
    ObservationRegistry observationRegistry() {
      return TestObservationRegistry.create();
    }
  }
}
